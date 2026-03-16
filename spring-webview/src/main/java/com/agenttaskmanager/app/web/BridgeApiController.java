package com.agenttaskmanager.app.web;

import com.agenttaskmanager.app.model.bridge.BridgeClaim;
import com.agenttaskmanager.app.model.bridge.BridgeRunHandle;
import com.agenttaskmanager.app.bridge.BridgeExecutionStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/bridge")
public class BridgeApiController {

  private final BridgeExecutionStore executionStore;
  private final ObjectMapper objectMapper;

  public BridgeApiController(BridgeExecutionStore executionStore, ObjectMapper objectMapper) {
    this.executionStore = executionStore;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/sessions/register")
  public BridgeSessionResponse registerSession(@Valid @RequestBody BridgeSessionRequest request) {
    String sessionId = request.sessionId() == null || request.sessionId().isBlank()
        ? "session-" + UUID.randomUUID()
        : request.sessionId().strip();
    executionStore.upsertBridgeSession(
        sessionId,
        request.agentId(),
        request.status() == null || request.status().isBlank() ? "online" : request.status().strip(),
        request.hostName(),
        request.clientName(),
        request.repoPath(),
        capabilitiesJson(request.capabilities())
    );
    return new BridgeSessionResponse(sessionId, request.agentId(), "registered");
  }

  @PostMapping("/sessions/heartbeat")
  public Map<String, String> heartbeat(@Valid @RequestBody BridgeHeartbeatRequest request) {
    executionStore.upsertBridgeSession(
        request.sessionId(),
        request.agentId(),
        request.status() == null || request.status().isBlank() ? "online" : request.status().strip(),
        request.hostName(),
        request.clientName(),
        request.repoPath(),
        capabilitiesJson(request.capabilities())
    );
    return Map.of("status", "ok");
  }

  @PostMapping("/claims/next")
  public BridgeClaimResponse claimNext(@Valid @RequestBody BridgeClaimRequest request) {
    executionStore.upsertBridgeSession(
        request.sessionId(),
        request.agentId(),
        "online",
        request.hostName(),
        request.clientName(),
        request.repoPath(),
        capabilitiesJson(request.capabilities())
    );
    Optional<BridgeClaim> claim = executionStore.claimNextQueued(
        request.agentId(),
        request.bridgeTarget()
    );
    if (claim.isEmpty()) {
      return new BridgeClaimResponse(null, null);
    }
    BridgeClaim next = claim.get();
    BridgeRunHandle runHandle = executionStore.startRun(
        next.requestId(),
        request.sessionId(),
        request.agentId(),
        next.threadKey()
    );
    return new BridgeClaimResponse(
        new ClaimedPrompt(
            next.requestId(),
            next.projectKey(),
            next.repoPath(),
            next.bridgeTarget(),
            next.threadKey(),
            next.resumeSessionId(),
            next.requestedBy(),
            next.executionMode(),
            next.promptText(),
            runHandle.runId()
        ),
        request.sessionId()
    );
  }

  @PostMapping("/runs/{runId}/messages")
  public Map<String, String> appendMessage(
      @PathVariable long runId,
      @Valid @RequestBody BridgeMessageRequest request
  ) {
    executionStore.appendPromptMessage(
        request.requestId(),
        runId,
        request.messageKind(),
        request.senderName(),
        request.body()
    );
    if (request.threadSessionId() != null && !request.threadSessionId().isBlank()) {
      executionStore.recordThreadSession(request.requestId(), runId, request.threadSessionId());
    }
    return Map.of("status", "ok");
  }

  @PostMapping("/runs/{runId}/complete")
  public Map<String, String> completeRun(
      @PathVariable long runId,
      @Valid @RequestBody BridgeRunResultRequest request
  ) {
    executionStore.completeRun(
        request.requestId(),
        runId,
        request.summary(),
        request.threadSessionId()
    );
    return Map.of("status", "ok");
  }

  @PostMapping("/runs/{runId}/fail")
  public Map<String, String> failRun(
      @PathVariable long runId,
      @Valid @RequestBody BridgeRunFailureRequest request
  ) {
    executionStore.failRun(
        request.requestId(),
        runId,
        request.exitCode(),
        request.summary(),
        request.threadSessionId()
    );
    return Map.of("status", "ok");
  }

  private String capabilitiesJson(Map<String, Object> capabilities) {
    try {
      return objectMapper.writeValueAsString(capabilities == null ? Map.of() : capabilities);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to serialize bridge capabilities.", exception);
    }
  }

  public record BridgeSessionRequest(
      String sessionId,
      @NotBlank String agentId,
      String hostName,
      @NotBlank String clientName,
      String repoPath,
      String status,
      Map<String, Object> capabilities
  ) {
  }

  public record BridgeHeartbeatRequest(
      @NotBlank String sessionId,
      @NotBlank String agentId,
      String hostName,
      @NotBlank String clientName,
      String repoPath,
      String status,
      Map<String, Object> capabilities
  ) {
  }

  public record BridgeClaimRequest(
      @NotBlank String sessionId,
      @NotBlank String agentId,
      String hostName,
      @NotBlank String clientName,
      String repoPath,
      @NotBlank String bridgeTarget,
      Map<String, Object> capabilities
  ) {
  }

  public record BridgeClaimResponse(ClaimedPrompt claim, String sessionId) {
  }

  public record ClaimedPrompt(
      String requestId,
      String projectKey,
      String repoPath,
      String bridgeTarget,
      String threadKey,
      String resumeSessionId,
      String requestedBy,
      String executionMode,
      String promptText,
      long runId
  ) {
  }

  public record BridgeMessageRequest(
      @NotBlank String requestId,
      @NotBlank String messageKind,
      @NotBlank String senderName,
      @NotBlank String body,
      String threadSessionId
  ) {
  }

  public record BridgeRunResultRequest(
      @NotBlank String requestId,
      @NotBlank String summary,
      String threadSessionId
  ) {
  }

  public record BridgeRunFailureRequest(
      @NotBlank String requestId,
      int exitCode,
      @NotBlank String summary,
      String threadSessionId
  ) {
  }

  public record BridgeSessionResponse(String sessionId, String agentId, String status) {
  }
}
