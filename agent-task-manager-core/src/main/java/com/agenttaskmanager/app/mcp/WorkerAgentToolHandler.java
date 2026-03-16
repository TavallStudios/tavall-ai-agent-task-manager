package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.orchestration.CleanupReviewService;
import com.agenttaskmanager.app.orchestration.WorkerLifecycleService;
import com.agenttaskmanager.app.model.orchestration.CleanupReviewResult;
import com.agenttaskmanager.app.model.orchestration.DeadWorkerRecord;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.model.orchestration.WorkerCheckIn;
import com.agenttaskmanager.app.model.orchestration.WorkerTransportKind;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkerAgentToolHandler extends McpToolSupport implements McpToolProvider {

  private final WorkerLifecycleService workerLifecycleService;
  private final CleanupReviewService cleanupReviewService;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public WorkerAgentToolHandler(
      WorkerLifecycleService workerLifecycleService,
      CleanupReviewService cleanupReviewService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.workerLifecycleService = workerLifecycleService;
    this.cleanupReviewService = cleanupReviewService;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        new SyncToolSpecification(
            tool(
                "submitWorkerCheckIn",
                "Submit a worker progress check-in.",
                Map.of(
                    "workerTaskId", stringProperty("Worker task id."),
                    "taskId", stringProperty("Batch task id."),
                    "agentId", stringProperty("Agent id."),
                    "status", stringProperty("Lifecycle status."),
                    "summary", stringProperty("Check-in summary."),
                    "details", Map.of("type", "object", "description", "Structured details.")
                ),
                List.of("workerTaskId", "taskId", "agentId", "status", "summary")
            ),
            (exchange, request) -> resultFactory.toolResult(submitWorkerCheckIn(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "heartbeatWorker",
                "Refresh the worker lease heartbeat.",
                Map.of("workerTaskId", stringProperty("Worker task id."), "agentId", stringProperty("Agent id.")),
                List.of("workerTaskId", "agentId")
            ),
            (exchange, request) -> resultFactory.toolResult(heartbeatWorker(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "markWorkerDead",
                "Mark a worker dead after heartbeat timeout.",
                Map.of("workerTaskId", stringProperty("Worker task id."), "summary", stringProperty("Failure summary.")),
                List.of("workerTaskId", "summary")
            ),
            (exchange, request) -> resultFactory.toolResult(markWorkerDead(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "registerWorker",
                "Register a worker session.",
                Map.of(
                    "sessionId", stringProperty("Session id."),
                    "agentId", stringProperty("Agent id."),
                    "hostName", stringProperty("Host name."),
                    "clientName", stringProperty("Client name."),
                    "repoPath", stringProperty("Repository path."),
                    "transportKind", stringProperty("Transport kind.")
                ),
                List.of("sessionId", "agentId", "hostName", "clientName", "transportKind")
            ),
            (exchange, request) -> resultFactory.toolResult(registerWorker(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "updateWorkerLease",
                "Refresh a worker lease and heartbeat.",
                Map.of("workerTaskId", stringProperty("Worker task id."), "agentId", stringProperty("Agent id.")),
                List.of("workerTaskId", "agentId")
            ),
            (exchange, request) -> resultFactory.toolResult(heartbeatWorker(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "registerCleanupAgent",
                "Register the cleanup agent session.",
                Map.of(
                    "sessionId", stringProperty("Session id."),
                    "agentId", stringProperty("Agent id."),
                    "hostName", stringProperty("Host name."),
                    "clientName", stringProperty("Client name.")
                ),
                List.of("sessionId", "agentId", "hostName", "clientName")
            ),
            (exchange, request) -> resultFactory.toolResult(registerCleanupAgent(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "submitCleanupReview",
                "Store a cleanup review outcome.",
                Map.of(
                    "cleanupReviewId", stringProperty("Cleanup review id."),
                    "status", stringProperty("Lifecycle status."),
                    "summary", stringProperty("Review summary."),
                    "findings", arrayProperty("Review findings.", stringProperty("Finding text."))
                ),
                List.of("cleanupReviewId", "status", "summary", "findings")
            ),
            (exchange, request) -> resultFactory.toolResult(submitCleanupReview(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "markCleanupReviewRequired",
                "Mark cleanup review as required.",
                Map.of("cleanupReviewId", stringProperty("Cleanup review id."), "reason", stringProperty("Reason.")),
                List.of("cleanupReviewId", "reason")
            ),
            (exchange, request) -> resultFactory.toolResult(markCleanupReviewRequired(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "markCleanupApproved",
                "Approve cleanup review.",
                Map.of("cleanupReviewId", stringProperty("Cleanup review id."), "summary", stringProperty("Summary.")),
                List.of("cleanupReviewId", "summary")
            ),
            (exchange, request) -> resultFactory.toolResult(markCleanupApproved(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "markCleanupRejected",
                "Reject cleanup review and require rework.",
                Map.of(
                    "cleanupReviewId", stringProperty("Cleanup review id."),
                    "summary", stringProperty("Summary."),
                    "findings", arrayProperty("Findings.", stringProperty("Finding text."))
                ),
                List.of("cleanupReviewId", "summary", "findings")
            ),
            (exchange, request) -> resultFactory.toolResult(markCleanupRejected(request.arguments()))
        )
    );
  }

  private WorkerCheckInResponse submitWorkerCheckIn(Map<String, Object> arguments) {
    WorkerCheckInRequest request = payloadMapper.map(arguments, WorkerCheckInRequest.class);
    WorkerCheckIn checkIn = workerLifecycleService.submitWorkerCheckIn(
        request.workerTaskId(),
        request.taskId(),
        request.agentId(),
        TaskLifecycleStatus.valueOf(request.status()),
        request.summary(),
        request.details() == null ? Map.of() : request.details()
    );
    return new WorkerCheckInResponse(checkIn);
  }

  private StatusResponse heartbeatWorker(Map<String, Object> arguments) {
    WorkerHeartbeatRequest request = payloadMapper.map(arguments, WorkerHeartbeatRequest.class);
    workerLifecycleService.heartbeatWorker(request.workerTaskId(), request.agentId());
    return new StatusResponse("ok");
  }

  private DeadWorkerResponse markWorkerDead(Map<String, Object> arguments) {
    WorkerDeadRequest request = payloadMapper.map(arguments, WorkerDeadRequest.class);
    DeadWorkerRecord deadWorkerRecord = workerLifecycleService.markWorkerDead(request.workerTaskId(), request.summary());
    return new DeadWorkerResponse(deadWorkerRecord);
  }

  private StatusResponse registerWorker(Map<String, Object> arguments) {
    WorkerRegistrationRequest request = payloadMapper.map(arguments, WorkerRegistrationRequest.class);
    workerLifecycleService.registerWorker(
        request.sessionId(),
        request.agentId(),
        request.hostName(),
        request.clientName(),
        request.repoPath(),
        WorkerTransportKind.valueOf(request.transportKind())
    );
    return new StatusResponse("registered");
  }

  private StatusResponse registerCleanupAgent(Map<String, Object> arguments) {
    CleanupRegistrationRequest request = payloadMapper.map(arguments, CleanupRegistrationRequest.class);
    workerLifecycleService.registerCleanupAgent(
        request.sessionId(),
        request.agentId(),
        request.hostName(),
        request.clientName()
    );
    return new StatusResponse("registered");
  }

  private CleanupReviewResultResponse submitCleanupReview(Map<String, Object> arguments) {
    CleanupReviewUpdateRequest request = payloadMapper.map(arguments, CleanupReviewUpdateRequest.class);
    CleanupReviewResult result = cleanupReviewService.submitCleanupReview(
        request.cleanupReviewId(),
        TaskLifecycleStatus.valueOf(request.status()),
        request.summary(),
        request.findings()
    );
    return new CleanupReviewResultResponse(result);
  }

  private CleanupReviewResultResponse markCleanupReviewRequired(Map<String, Object> arguments) {
    CleanupReviewRequiredRequest request = payloadMapper.map(arguments, CleanupReviewRequiredRequest.class);
    return new CleanupReviewResultResponse(
        cleanupReviewService.markCleanupReviewRequired(request.cleanupReviewId(), request.reason())
    );
  }

  private CleanupReviewResultResponse markCleanupApproved(Map<String, Object> arguments) {
    CleanupApprovalRequest request = payloadMapper.map(arguments, CleanupApprovalRequest.class);
    return new CleanupReviewResultResponse(
        cleanupReviewService.markCleanupApproved(request.cleanupReviewId(), request.summary())
    );
  }

  private CleanupReviewResultResponse markCleanupRejected(Map<String, Object> arguments) {
    CleanupReviewUpdateRequest request = payloadMapper.map(arguments, CleanupReviewUpdateRequest.class);
    return new CleanupReviewResultResponse(
        cleanupReviewService.markCleanupRejected(request.cleanupReviewId(), request.summary(), request.findings())
    );
  }

  private Map<String, Object> stringProperty(String description) {
    return super.schemaFactory.stringProperty(description);
  }

  private Map<String, Object> arrayProperty(String description, Map<String, Object> itemSchema) {
    return super.schemaFactory.arrayProperty(description, itemSchema);
  }
}

record WorkerCheckInRequest(
    String workerTaskId,
    String taskId,
    String agentId,
    String status,
    String summary,
    Map<String, Object> details
) {
}

record WorkerHeartbeatRequest(String workerTaskId, String agentId) {
}

record WorkerDeadRequest(String workerTaskId, String summary) {
}

record WorkerRegistrationRequest(
    String sessionId,
    String agentId,
    String hostName,
    String clientName,
    String repoPath,
    String transportKind
) {
}

record CleanupRegistrationRequest(String sessionId, String agentId, String hostName, String clientName) {
}

record CleanupReviewUpdateRequest(String cleanupReviewId, String status, String summary, List<String> findings) {
}

record CleanupReviewRequiredRequest(String cleanupReviewId, String reason) {
}

record CleanupApprovalRequest(String cleanupReviewId, String summary) {
}

record WorkerCheckInResponse(WorkerCheckIn checkIn) {
}

record DeadWorkerResponse(DeadWorkerRecord deadWorkerRecord) {
}

record CleanupReviewResultResponse(CleanupReviewResult cleanupReviewResult) {
}

record StatusResponse(String status) {
}
