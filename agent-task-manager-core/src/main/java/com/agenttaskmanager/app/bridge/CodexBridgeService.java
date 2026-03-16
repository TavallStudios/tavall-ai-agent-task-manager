package com.agenttaskmanager.app.bridge;

import com.agenttaskmanager.app.config.CodexBridgeProperties;
import com.agenttaskmanager.app.model.bridge.BridgeClaim;
import com.agenttaskmanager.app.model.bridge.BridgeRunHandle;
import com.agenttaskmanager.app.model.bridge.BridgeStatusSnapshot;
import com.agenttaskmanager.app.orchestration.PromptMemoryCaptureService;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@Service
public class CodexBridgeService {

  private static final Logger LOGGER = LoggerFactory.getLogger(CodexBridgeService.class);

  private final CodexBridgeProperties properties;
  private final BridgeExecutionStore executionStore;
  private final CodexExecCommandFactory commandFactory;
  private final CodexJsonEventParser eventParser;
  private final BridgePromptMemoryService promptMemoryService;
  private final PromptMemoryCaptureService promptMemoryCaptureService;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicReference<ActiveRun> activeRun = new AtomicReference<>();

  private volatile String sessionId;
  private volatile String agentId;
  private volatile String sessionStatus = "offline";

  public CodexBridgeService(
      CodexBridgeProperties properties,
      BridgeExecutionStore executionStore,
      CodexExecCommandFactory commandFactory,
      CodexJsonEventParser eventParser,
      BridgePromptMemoryService promptMemoryService,
      PromptMemoryCaptureService promptMemoryCaptureService
  ) {
    this.properties = properties;
    this.executionStore = executionStore;
    this.commandFactory = commandFactory;
    this.eventParser = eventParser;
    this.promptMemoryService = promptMemoryService;
    this.promptMemoryCaptureService = promptMemoryCaptureService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    this.sessionId = "session-" + UUID.randomUUID();
    this.agentId = resolveAgentId();
    if (!properties.isEnabled()) {
      this.sessionStatus = "disabled";
      executionStore.upsertAgentSession(sessionId, agentId, "disabled", hostName(), "AgentTaskManager");
      return;
    }
    executionStore.upsertAgentSession(sessionId, agentId, "online", hostName(), "AgentTaskManager");
    this.sessionStatus = "online";
  }

  @Scheduled(
      initialDelayString = "${app.bridge.poll-interval-ms:5000}",
      fixedDelayString = "${app.bridge.poll-interval-ms:5000}"
  )
  public void pollQueue() {
    if (sessionId == null || !properties.isEnabled()) {
      return;
    }
    executionStore.heartbeatAgentSession(sessionId, "online");
    ActiveRun current = activeRun.get();
    if (current != null && !current.future().isDone()) {
      return;
    }
    if (current != null && current.future().isDone()) {
      activeRun.compareAndSet(current, null);
    }
    Optional<BridgeClaim> claim = executionStore.claimNextQueued(agentId, "remote-headless");
    if (claim.isEmpty()) {
      return;
    }
    BridgeClaim next = claim.get();
    BridgeRunHandle runHandle = executionStore.startRun(
        next.requestId(),
        sessionId,
        agentId,
        next.threadKey()
    );
    Future<?> future = worker.submit(() -> executeClaim(next, runHandle));
    activeRun.set(new ActiveRun(next.requestId(), runHandle.runId(), future));
  }

  public BridgeStatusSnapshot getStatus() {
    ActiveRun current = activeRun.get();
    return new BridgeStatusSnapshot(
        properties.isEnabled(),
        "online".equals(sessionStatus),
        agentId,
        sessionId,
        sessionStatus,
        current == null ? null : current.requestId(),
        current == null ? null : current.runId()
    );
  }

  private void executeClaim(BridgeClaim claim, BridgeRunHandle runHandle) {
    Path outputFile = null;
    AtomicReference<String> threadSessionId = new AtomicReference<>(claim.resumeSessionId());
    try {
      outputFile = Files.createTempFile("agent-task-manager-codex-", ".txt");
      BridgePromptMemoryService.PreparedPrompt preparedPrompt = promptMemoryService.preparePrompt(
          claim.projectKey(),
          claim.executionMode(),
          claim.promptText()
      );
      executionStore.appendPromptMessage(
          claim.requestId(),
          runHandle.runId(),
          "memory-lookup",
          "qdrant-memory",
          truncate(preparedPrompt.memorySummary())
      );
      promptMemoryCaptureService.captureProjectMemory(
          claim.projectKey(),
          claim.requestId(),
          null,
          "memory-lookup",
          preparedPrompt.memorySummary(),
          Map.of(
              "requestId", claim.requestId(),
              "runId", runHandle.runId(),
              "repoPath", claim.repoPath(),
              "bridgeTarget", claim.bridgeTarget()
          )
      );
      List<String> command = commandFactory.buildCommand(
          claim.projectKey(),
          Path.of(claim.repoPath()),
          claim.executionMode(),
          outputFile,
          claim.resumeSessionId()
      );
      command.add(preparedPrompt.envelope());
      ProcessBuilder processBuilder = new ProcessBuilder(command);
      processBuilder.redirectErrorStream(false);
      Process process = processBuilder.start();
      executionStore.appendPromptMessage(
          claim.requestId(),
          runHandle.runId(),
          "bridge-status",
          "codex-bridge",
          "Started Codex bridge run on " + claim.repoPath()
      );

      Thread stderrThread = Thread.ofPlatform()
          .name("codex-bridge-stderr-" + runHandle.runId())
          .start(() -> consumeStderr(process.getErrorStream(), claim, runHandle));
      consumeStdout(process.getInputStream(), claim, runHandle, threadSessionId);
      int exitCode = process.waitFor();
      stderrThread.join();
      String finalMessage = Files.exists(outputFile) ? Files.readString(outputFile, StandardCharsets.UTF_8).strip() : "";
      if (!finalMessage.isBlank()) {
        executionStore.appendPromptMessage(
            claim.requestId(),
            runHandle.runId(),
            "final-response",
            "codex",
            truncate(finalMessage)
        );
        promptMemoryCaptureService.captureProjectMemory(
            claim.projectKey(),
            claim.requestId(),
            null,
            "final-response",
            finalMessage,
            Map.of(
                "requestId", claim.requestId(),
                "runId", runHandle.runId(),
                "repoPath", claim.repoPath(),
                "bridgeTarget", claim.bridgeTarget(),
                "sender", "codex"
            )
        );
      }

      String summary = finalMessage.isBlank()
          ? "Codex run completed with exit code " + exitCode
          : truncate(finalMessage);
      if (exitCode == 0) {
        executionStore.completeRun(claim.requestId(), runHandle.runId(), summary, threadSessionId.get());
      } else {
        executionStore.failRun(claim.requestId(), runHandle.runId(), exitCode, summary, threadSessionId.get());
      }
    } catch (Exception exception) {
      LOGGER.error("Codex bridge failed for {}", claim.requestId(), exception);
      executionStore.appendPromptMessage(
          claim.requestId(),
          runHandle.runId(),
          "bridge-error",
          "codex-bridge",
          truncate(exception.getMessage() == null ? exception.toString() : exception.getMessage())
      );
      executionStore.failRun(
          claim.requestId(),
          runHandle.runId(),
          -1,
          "Bridge failure: " + truncate(exception.getMessage() == null ? exception.toString() : exception.getMessage()),
          threadSessionId.get()
      );
    } finally {
      if (outputFile != null) {
        try {
          Files.deleteIfExists(outputFile);
        } catch (IOException ignored) {
          LOGGER.debug("Failed to delete {}", outputFile, ignored);
        }
      }
    }
  }

  private void consumeStdout(
      InputStream inputStream,
      BridgeClaim claim,
      BridgeRunHandle runHandle,
      AtomicReference<String> threadSessionId
  )
      throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        List<CodexEventMessage> messages = eventParser.parseLine(line);
        for (CodexEventMessage message : messages) {
          if ("thread-started".equals(message.kind())) {
            String parsedThreadId = parseThreadId(message.body());
            if (parsedThreadId != null) {
              threadSessionId.set(parsedThreadId);
              executionStore.recordThreadSession(claim.requestId(), runHandle.runId(), parsedThreadId);
            }
          }
          executionStore.appendPromptMessage(
              claim.requestId(),
              runHandle.runId(),
              message.kind(),
              message.sender(),
              truncate(message.body())
          );
        }
      }
    }
  }

  private void consumeStderr(InputStream inputStream, BridgeClaim claim, BridgeRunHandle runHandle) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank() || isIgnorableStderr(line)) {
          continue;
        }
        executionStore.appendPromptMessage(
            claim.requestId(),
            runHandle.runId(),
            "stderr",
            "codex-bridge",
            truncate(line)
        );
      }
    } catch (IOException exception) {
      executionStore.appendPromptMessage(
          claim.requestId(),
          runHandle.runId(),
          "bridge-error",
          "codex-bridge",
          truncate(exception.getMessage() == null ? exception.toString() : exception.getMessage())
      );
    }
  }

  private String truncate(String value) {
    if (value == null) {
      return "";
    }
    String normalized = value.strip();
    if (normalized.length() <= properties.getMaxMessageChars()) {
      return normalized;
    }
    return normalized.substring(0, properties.getMaxMessageChars() - 3) + "...";
  }

  private static boolean isIgnorableStderr(String line) {
    return line.contains("Failed to kill MCP process group")
        || line.contains("Failed to delete shell snapshot")
        || line.contains("Error reading from stream: serde error expected value")
        || line.contains("failed to unwatch /home/ubuntu/.codex/skills/.system");
  }

  private static String parseThreadId(String body) {
    if (body == null) {
      return null;
    }
    String prefix = "Started thread ";
    return body.startsWith(prefix) ? body.substring(prefix.length()).strip() : null;
  }

  private String resolveAgentId() {
    if (properties.getAgentId() != null && !properties.getAgentId().isBlank()) {
      return properties.getAgentId().strip();
    }
    return "codex-bridge@" + hostName();
  }

  private static String hostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception ignored) {
      return "unknown-host";
    }
  }

  @PreDestroy
  public void shutdown() {
    if (sessionId != null) {
      executionStore.heartbeatAgentSession(sessionId, properties.isEnabled() ? "offline" : "disabled");
      sessionStatus = properties.isEnabled() ? "offline" : "disabled";
    }
    worker.shutdownNow();
  }

  private record ActiveRun(String requestId, Long runId, Future<?> future) {}
}
