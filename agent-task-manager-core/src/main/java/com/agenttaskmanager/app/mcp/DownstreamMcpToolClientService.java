package com.agenttaskmanager.app.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

@Service
public class DownstreamMcpToolClientService {

  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  private final DirectRepoToolExecutionService directRepoToolExecutionService;
  private final McpJsonMapper mcpJsonMapper;
  private final McpServerProcessConfigurationService processConfigurationService;

  public DownstreamMcpToolClientService(
      DirectRepoToolExecutionService directRepoToolExecutionService,
      McpJsonMapper mcpJsonMapper,
      McpServerProcessConfigurationService processConfigurationService
  ) {
    this.directRepoToolExecutionService = directRepoToolExecutionService;
    this.mcpJsonMapper = mcpJsonMapper;
    this.processConfigurationService = processConfigurationService;
  }

  public List<DownstreamMcpToolResult> callTools(String projectKey, List<DownstreamMcpToolCall> calls) {
    if (calls == null || calls.isEmpty()) {
      return List.of();
    }

    List<IndexedCall> indexedCalls = IntStream.range(0, calls.size())
        .mapToObj(index -> new IndexedCall(index, calls.get(index)))
        .toList();

    ExecutorService executor = Executors.newFixedThreadPool(Math.min(indexedCalls.size(), 6));
    try {
      List<CompletableFuture<IndexedResult>> futures = indexedCalls.stream()
          .map(indexedCall -> CompletableFuture.supplyAsync(
              () -> new IndexedResult(indexedCall.index(), callTool(projectKey, indexedCall.call())),
              executor
          ))
          .toList();

      Map<Integer, DownstreamMcpToolResult> byIndex = new HashMap<>();
      futures.stream()
          .map(CompletableFuture::join)
          .forEach(result -> byIndex.put(result.index(), result.result()));

      return indexedCalls.stream()
          .map(indexedCall -> byIndex.getOrDefault(indexedCall.index(), missingResult(indexedCall.call())))
          .toList();
    } finally {
      executor.shutdownNow();
    }
  }

  public DownstreamMcpToolResult callTool(String projectKey, DownstreamMcpToolCall call) {
    long startedAt = System.nanoTime();
    StringBuilder stderr = new StringBuilder();
    try {
      McpServerProcessConfiguration configuration = processConfigurationService.resolve(
          call.serverName(),
          projectKey
      );
      if (shouldUseDirectFallback(call, configuration)) {
        return directRepoToolExecutionService.executeFallback(
            call,
            stderr.toString(),
            new IllegalStateException("Direct fallback selected for local repo tool execution."),
            startedAt
        );
      }
      StdioClientTransport transport = new StdioClientTransport(
          serverParameters(configuration, List.of(new IndexedCall(0, call))),
          mcpJsonMapper
      );
      transport.setStdErrorHandler(line -> appendLine(stderr, line));

      try (McpSyncClient client = McpClient.sync(transport)
          .requestTimeout(TIMEOUT)
          .initializationTimeout(TIMEOUT)
          .clientInfo(new Implementation("AgentTaskManager Harness", "0.1.0"))
          .build()) {
        client.initialize();
        CallToolResult result = client.callTool(new CallToolRequest(call.toolName(), call.arguments()));
        return new DownstreamMcpToolResult(
            call.key(),
            call.serverName(),
            call.toolName(),
            Boolean.TRUE.equals(result.isError()) ? "error" : "completed",
            result.structuredContent(),
            textContent(result),
            stderr.toString(),
            null,
            durationMs(startedAt)
        );
      }
    } catch (Exception exception) {
      return directRepoToolExecutionService.executeFallback(call, stderr.toString(), exception, startedAt);
    }
  }

  private boolean shouldUseDirectFallback(
      DownstreamMcpToolCall call,
      McpServerProcessConfiguration configuration
  ) {
    if (!directRepoToolExecutionService.supports(call)) {
      return false;
    }
    String command = configuration.command();
    if (command == null || command.isBlank()) {
      return true;
    }
    if ("git".equals(call.serverName()) && isBareCommand(command)) {
      return true;
    }
    return isBareCommand(command) && !isCommandAvailable(command);
  }

  private DownstreamMcpToolResult missingResult(DownstreamMcpToolCall call) {
    return new DownstreamMcpToolResult(
        call.key(),
        call.serverName(),
        call.toolName(),
        "error",
        null,
        null,
        "",
        "No MCP result was produced.",
        0
    );
  }

  private ServerParameters serverParameters(McpServerProcessConfiguration configuration, List<IndexedCall> calls) {
    ServerParameters.Builder builder = ServerParameters.builder(configuration.command());
    List<String> args = new ArrayList<>(configuration.args());
    if ("filesystem".equals(configuration.serverName())) {
      args.addAll(filesystemRoots(calls));
    }
    if (!args.isEmpty()) {
      builder.args(args);
    }
    if (!configuration.env().isEmpty()) {
      builder.env(configuration.env());
    }
    return builder.build();
  }

  private List<String> filesystemRoots(List<IndexedCall> calls) {
    LinkedHashSet<String> roots = new LinkedHashSet<>();
    for (IndexedCall call : calls) {
      Map<String, Object> arguments = call.call().arguments();
      Object singlePath = arguments.get("path");
      if (singlePath instanceof String path && !path.isBlank()) {
        roots.add(normalizeFilesystemRoot(path));
      }
      Object multiplePaths = arguments.get("paths");
      if (multiplePaths instanceof List<?> list) {
        list.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .filter(path -> !path.isBlank())
            .map(this::normalizeFilesystemRoot)
            .forEach(roots::add);
      }
    }
    return roots.stream().toList();
  }

  private String normalizeFilesystemRoot(String path) {
    if (path.startsWith("/")) {
      return path;
    }
    return Path.of(path).toAbsolutePath().normalize().toString();
  }

  private String textContent(CallToolResult result) {
    if (result.content() == null) {
      return "";
    }
    return result.content().stream()
        .filter(TextContent.class::isInstance)
        .map(TextContent.class::cast)
        .map(TextContent::text)
        .collect(Collectors.joining("\n"));
  }

  private void appendLine(StringBuilder sink, String line) {
    if (sink.length() >= 4000) {
      return;
    }
    if (!sink.isEmpty()) {
      sink.append('\n');
    }
    sink.append(line);
  }

  private long durationMs(long startedAt) {
    return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
  }

  private boolean isBareCommand(String command) {
    return !command.contains("\\") && !command.contains("/") && !command.contains(":");
  }

  private boolean isCommandAvailable(String command) {
    for (String candidate : executableCandidates(command)) {
      for (String entry : pathEntries()) {
        try {
          Path path = Path.of(entry, candidate);
          if (Files.isRegularFile(path) && Files.isExecutable(path)) {
            return true;
          }
        } catch (InvalidPathException ignored) {
          // Skip malformed PATH entries.
        }
      }
    }
    return false;
  }

  private List<String> executableCandidates(String command) {
    if (!isWindows() || command.contains(".")) {
      return List.of(command);
    }
    String pathExt = System.getenv("PATHEXT");
    if (pathExt == null || pathExt.isBlank()) {
      return List.of(command, command + ".exe", command + ".cmd", command + ".bat");
    }
    return java.util.Arrays.stream(pathExt.split(";"))
        .map(String::trim)
        .filter(extension -> !extension.isBlank())
        .map(String::toLowerCase)
        .map(extension -> command + extension)
        .toList();
  }

  private List<String> pathEntries() {
    String path = System.getenv("PATH");
    if (path == null || path.isBlank()) {
      return List.of();
    }
    String separator = isWindows() ? ";" : java.io.File.pathSeparator;
    return java.util.Arrays.stream(path.split(java.util.regex.Pattern.quote(separator)))
        .map(String::trim)
        .filter(entry -> !entry.isBlank())
        .toList();
  }

  private boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }

  private record IndexedCall(int index, DownstreamMcpToolCall call) {
  }

  private record IndexedResult(int index, DownstreamMcpToolResult result) {
  }
}
