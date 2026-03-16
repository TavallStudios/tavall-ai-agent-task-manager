package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.orchestration.TaskPoolService;
import com.agenttaskmanager.app.model.orchestration.CleanupReviewTask;
import com.agenttaskmanager.app.model.orchestration.OverseerTaskBatch;
import com.agenttaskmanager.app.model.orchestration.TaskAssignment;
import com.agenttaskmanager.app.model.orchestration.WorkerTask;
import com.agenttaskmanager.app.model.orchestration.WorkerTransportKind;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TaskPoolToolHandler extends McpToolSupport implements McpToolProvider {

  private final TaskPoolService taskPoolService;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public TaskPoolToolHandler(
      TaskPoolService taskPoolService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.taskPoolService = taskPoolService;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        new SyncToolSpecification(
            tool(
                "createTaskBatch",
                "Create an overseer task batch and queue worker tasks.",
                Map.of(
                    "projectKey", stringProperty("Project key."),
                    "sourceRepo", stringProperty("Repository path."),
                    "title", stringProperty("Batch title."),
                    "multiAgentEnabled", booleanProperty("Whether fan-out is enabled."),
                    "workerRoles", arrayProperty("Worker role names.", stringProperty("Role name."))
                ),
                List.of("projectKey", "sourceRepo", "title", "workerRoles")
            ),
            (exchange, request) -> resultFactory.toolResult(createTaskBatch(request.arguments()))
        ),
        new SyncToolSpecification(
            tool("claimWorkerTask", "Claim the next worker task in a batch.", Map.of("taskId", stringProperty("Batch task id.")), List.of("taskId")),
            (exchange, request) -> resultFactory.toolResult(claimWorkerTask(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "assignWorkerTask",
                "Assign a worker task to an agent and create a lease.",
                Map.of(
                    "workerTaskId", stringProperty("Worker task id."),
                    "agentId", stringProperty("Agent id."),
                    "transportKind", stringProperty("Transport kind."),
                    "sessionId", stringProperty("Session id.")
                ),
                List.of("workerTaskId", "agentId", "transportKind", "sessionId")
            ),
            (exchange, request) -> resultFactory.toolResult(assignWorkerTask(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "reassignWorkerTask",
                "Reassign a worker task after timeout or failure.",
                Map.of("workerTaskId", stringProperty("Worker task id."), "summary", stringProperty("Reassignment summary.")),
                List.of("workerTaskId", "summary")
            ),
            (exchange, request) -> resultFactory.toolResult(reassignWorkerTask(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "completeWorkerTask",
                "Mark a worker task completed.",
                Map.of("workerTaskId", stringProperty("Worker task id."), "summary", stringProperty("Completion summary.")),
                List.of("workerTaskId", "summary")
            ),
            (exchange, request) -> resultFactory.toolResult(completeWorkerTask(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "failWorkerTask",
                "Mark a worker task failed.",
                Map.of("workerTaskId", stringProperty("Worker task id."), "summary", stringProperty("Failure summary.")),
                List.of("workerTaskId", "summary")
            ),
            (exchange, request) -> resultFactory.toolResult(failWorkerTask(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "deadLetterWorkerTask",
                "Route a worker task into the dead-letter state.",
                Map.of("workerTaskId", stringProperty("Worker task id."), "summary", stringProperty("Dead-letter summary.")),
                List.of("workerTaskId", "summary")
            ),
            (exchange, request) -> resultFactory.toolResult(deadLetterWorkerTask(request.arguments()))
        ),
        new SyncToolSpecification(
            tool(
                "createCleanupReviewTask",
                "Create a cleanup review task for a diff artifact.",
                Map.of(
                    "taskId", stringProperty("Batch task id."),
                    "workerTaskId", stringProperty("Worker task id."),
                    "diffArtifactId", stringProperty("Diff artifact id.")
                ),
                List.of("taskId", "workerTaskId", "diffArtifactId")
            ),
            (exchange, request) -> resultFactory.toolResult(createCleanupReviewTask(request.arguments()))
        )
    );
  }

  private CreateTaskBatchResponse createTaskBatch(Map<String, Object> arguments) {
    CreateTaskBatchRequest request = payloadMapper.map(arguments, CreateTaskBatchRequest.class);
    OverseerTaskBatch batch = taskPoolService.createTaskBatch(
        request.projectKey(),
        request.sourceRepo(),
        request.title(),
        request.multiAgentEnabled(),
        request.workerRoles()
    );
    return new CreateTaskBatchResponse(batch);
  }

  private ClaimWorkerTaskResponse claimWorkerTask(Map<String, Object> arguments) {
    ClaimWorkerTaskRequest request = payloadMapper.map(arguments, ClaimWorkerTaskRequest.class);
    return new ClaimWorkerTaskResponse(taskPoolService.claimWorkerTask(request.taskId()));
  }

  private AssignWorkerTaskResponse assignWorkerTask(Map<String, Object> arguments) {
    AssignWorkerTaskRequest request = payloadMapper.map(arguments, AssignWorkerTaskRequest.class);
    TaskAssignment assignment = taskPoolService.assignWorkerTask(
        request.workerTaskId(),
        request.agentId(),
        WorkerTransportKind.valueOf(request.transportKind()),
        request.sessionId()
    );
    return new AssignWorkerTaskResponse(assignment);
  }

  private WorkerTaskResponse reassignWorkerTask(Map<String, Object> arguments) {
    WorkerTaskUpdateRequest request = payloadMapper.map(arguments, WorkerTaskUpdateRequest.class);
    return new WorkerTaskResponse(taskPoolService.reassignWorkerTask(request.workerTaskId(), request.summary()));
  }

  private WorkerTaskResponse completeWorkerTask(Map<String, Object> arguments) {
    WorkerTaskUpdateRequest request = payloadMapper.map(arguments, WorkerTaskUpdateRequest.class);
    return new WorkerTaskResponse(taskPoolService.completeWorkerTask(request.workerTaskId(), request.summary()));
  }

  private WorkerTaskResponse failWorkerTask(Map<String, Object> arguments) {
    WorkerTaskUpdateRequest request = payloadMapper.map(arguments, WorkerTaskUpdateRequest.class);
    return new WorkerTaskResponse(taskPoolService.failWorkerTask(request.workerTaskId(), request.summary()));
  }

  private WorkerTaskResponse deadLetterWorkerTask(Map<String, Object> arguments) {
    WorkerTaskUpdateRequest request = payloadMapper.map(arguments, WorkerTaskUpdateRequest.class);
    return new WorkerTaskResponse(taskPoolService.deadLetterWorkerTask(request.workerTaskId(), request.summary()));
  }

  private CleanupReviewTaskResponse createCleanupReviewTask(Map<String, Object> arguments) {
    CleanupReviewTaskRequest request = payloadMapper.map(arguments, CleanupReviewTaskRequest.class);
    CleanupReviewTask reviewTask = taskPoolService.createCleanupReviewTask(
        request.taskId(),
        request.workerTaskId(),
        request.diffArtifactId()
    );
    return new CleanupReviewTaskResponse(reviewTask);
  }

  private Map<String, Object> stringProperty(String description) {
    return super.schemaFactory.stringProperty(description);
  }

  private Map<String, Object> booleanProperty(String description) {
    return super.schemaFactory.booleanProperty(description);
  }

  private Map<String, Object> arrayProperty(String description, Map<String, Object> itemSchema) {
    return super.schemaFactory.arrayProperty(description, itemSchema);
  }
}

record CreateTaskBatchRequest(
    String projectKey,
    String sourceRepo,
    String title,
    boolean multiAgentEnabled,
    List<String> workerRoles
) {
}

record CreateTaskBatchResponse(OverseerTaskBatch batch) {
}

record ClaimWorkerTaskRequest(String taskId) {
}

record ClaimWorkerTaskResponse(WorkerTask workerTask) {
}

record AssignWorkerTaskRequest(String workerTaskId, String agentId, String transportKind, String sessionId) {
}

record AssignWorkerTaskResponse(TaskAssignment assignment) {
}

record WorkerTaskUpdateRequest(String workerTaskId, String summary) {
}

record WorkerTaskResponse(WorkerTask workerTask) {
}

record CleanupReviewTaskRequest(String taskId, String workerTaskId, String diffArtifactId) {
}

record CleanupReviewTaskResponse(CleanupReviewTask cleanupReviewTask) {
}
