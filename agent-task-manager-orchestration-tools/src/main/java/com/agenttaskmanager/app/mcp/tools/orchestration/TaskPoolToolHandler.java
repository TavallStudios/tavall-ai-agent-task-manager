package com.agenttaskmanager.app.mcp.tools.orchestration;

import com.agenttaskmanager.app.mcp.McpJsonSchemaFactory;
import com.agenttaskmanager.app.mcp.McpResultFactory;
import com.agenttaskmanager.app.mcp.McpToolPayloadMapper;
import com.agenttaskmanager.app.mcp.McpToolProvider;
import com.agenttaskmanager.app.mcp.McpToolSupport;
import com.agenttaskmanager.app.model.orchestration.CleanupReviewTask;
import com.agenttaskmanager.app.model.orchestration.CodexDelegationRunSnapshot;
import com.agenttaskmanager.app.model.orchestration.OverseerTaskBatch;
import com.agenttaskmanager.app.model.orchestration.TaskAssignment;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.model.orchestration.WorkerTransportKind;
import com.agenttaskmanager.app.orchestration.CodexDelegationRunService;
import com.agenttaskmanager.app.orchestration.TaskPoolService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TaskPoolToolHandler extends McpToolSupport implements McpToolProvider {

  private final TaskPoolService taskPoolService;
  private final CodexDelegationRunService codexDelegationRunService;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public TaskPoolToolHandler(
      TaskPoolService taskPoolService,
      CodexDelegationRunService codexDelegationRunService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.taskPoolService = taskPoolService;
    this.codexDelegationRunService = codexDelegationRunService;
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
                    "multiAgentEnabled", booleanProperty("Deprecated compatibility input. Ignored for canonical delegation flow."),
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
        false,
        request.workerRoles()
    );
    CodexDelegationRunSnapshot snapshot = codexDelegationRunService.startRun(
        batch.taskId(),
        request.projectKey(),
        request.sourceRepo(),
        request.title(),
        Map.of(
            "legacyTool", "createTaskBatch",
            "multiAgentEnabledRequested", request.multiAgentEnabled(),
            "workerRoles", request.workerRoles()
        )
    );
    codexDelegationRunService.appendEvent(
        snapshot.run().runId(),
        "compat.createTaskBatch",
        TaskLifecycleStatus.QUEUED,
        "Legacy createTaskBatch mapped to canonical delegation run.",
        Map.of("taskId", batch.taskId())
    );
    return new CreateTaskBatchResponse(batch, compatibility("createTaskBatch", snapshot.run().runId()));
  }

  private ClaimWorkerTaskResponse claimWorkerTask(Map<String, Object> arguments) {
    ClaimWorkerTaskRequest request = payloadMapper.map(arguments, ClaimWorkerTaskRequest.class);
    var workerTask = taskPoolService.claimWorkerTask(request.taskId());
    Optional<CodexDelegationRunSnapshot> snapshot = workerTask == null
        ? codexDelegationRunService.findLatestByTaskId(request.taskId())
        : codexDelegationRunService.appendEventByTaskId(
            workerTask.taskId(),
            "compat.claimWorkerTask",
            TaskLifecycleStatus.ASSIGNED,
            "Legacy claimWorkerTask mapped to canonical delegation run.",
            Map.of("workerTaskId", workerTask.workerTaskId(), "taskRole", workerTask.taskRole())
        );
    return new ClaimWorkerTaskResponse(workerTask, compatibility("claimWorkerTask", snapshot.map(run -> run.run().runId()).orElse(null)));
  }

  private AssignWorkerTaskResponse assignWorkerTask(Map<String, Object> arguments) {
    AssignWorkerTaskRequest request = payloadMapper.map(arguments, AssignWorkerTaskRequest.class);
    TaskAssignment assignment = taskPoolService.assignWorkerTask(
        request.workerTaskId(),
        request.agentId(),
        WorkerTransportKind.valueOf(request.transportKind()),
        request.sessionId()
    );
    Optional<CodexDelegationRunSnapshot> snapshot = codexDelegationRunService.appendEventByTaskId(
        assignment.taskId(),
        "compat.assignWorkerTask",
        TaskLifecycleStatus.ASSIGNED,
        "Legacy assignWorkerTask mapped to canonical delegation run.",
        Map.of("workerTaskId", assignment.workerTaskId(), "agentId", assignment.agentId())
    );
    return new AssignWorkerTaskResponse(assignment, compatibility("assignWorkerTask", snapshot.map(run -> run.run().runId()).orElse(null)));
  }

  private WorkerTaskResponse reassignWorkerTask(Map<String, Object> arguments) {
    WorkerTaskUpdateRequest request = payloadMapper.map(arguments, WorkerTaskUpdateRequest.class);
    var workerTask = taskPoolService.reassignWorkerTask(request.workerTaskId(), request.summary());
    Optional<CodexDelegationRunSnapshot> snapshot = codexDelegationRunService.appendEventByTaskId(
        workerTask.taskId(),
        "compat.reassignWorkerTask",
        TaskLifecycleStatus.REASSIGNED,
        "Legacy reassignWorkerTask mapped to canonical delegation run.",
        Map.of("workerTaskId", workerTask.workerTaskId(), "summary", request.summary())
    );
    return new WorkerTaskResponse(workerTask, compatibility("reassignWorkerTask", snapshot.map(run -> run.run().runId()).orElse(null)));
  }

  private WorkerTaskResponse completeWorkerTask(Map<String, Object> arguments) {
    WorkerTaskUpdateRequest request = payloadMapper.map(arguments, WorkerTaskUpdateRequest.class);
    var workerTask = taskPoolService.completeWorkerTask(request.workerTaskId(), request.summary());
    Optional<CodexDelegationRunSnapshot> snapshot = codexDelegationRunService.appendEventByTaskId(
        workerTask.taskId(),
        "compat.completeWorkerTask",
        TaskLifecycleStatus.COMPLETED,
        "Legacy completeWorkerTask mapped to canonical delegation run.",
        Map.of("workerTaskId", workerTask.workerTaskId(), "summary", request.summary())
    );
    return new WorkerTaskResponse(workerTask, compatibility("completeWorkerTask", snapshot.map(run -> run.run().runId()).orElse(null)));
  }

  private WorkerTaskResponse failWorkerTask(Map<String, Object> arguments) {
    WorkerTaskUpdateRequest request = payloadMapper.map(arguments, WorkerTaskUpdateRequest.class);
    var workerTask = taskPoolService.failWorkerTask(request.workerTaskId(), request.summary());
    Optional<CodexDelegationRunSnapshot> snapshot = codexDelegationRunService.appendEventByTaskId(
        workerTask.taskId(),
        "compat.failWorkerTask",
        TaskLifecycleStatus.FAILED,
        "Legacy failWorkerTask mapped to canonical delegation run.",
        Map.of("workerTaskId", workerTask.workerTaskId(), "summary", request.summary())
    );
    return new WorkerTaskResponse(workerTask, compatibility("failWorkerTask", snapshot.map(run -> run.run().runId()).orElse(null)));
  }

  private WorkerTaskResponse deadLetterWorkerTask(Map<String, Object> arguments) {
    WorkerTaskUpdateRequest request = payloadMapper.map(arguments, WorkerTaskUpdateRequest.class);
    var workerTask = taskPoolService.deadLetterWorkerTask(request.workerTaskId(), request.summary());
    Optional<CodexDelegationRunSnapshot> snapshot = codexDelegationRunService.appendEventByTaskId(
        workerTask.taskId(),
        "compat.deadLetterWorkerTask",
        TaskLifecycleStatus.DEAD_LETTER,
        "Legacy deadLetterWorkerTask mapped to canonical delegation run.",
        Map.of("workerTaskId", workerTask.workerTaskId(), "summary", request.summary())
    );
    return new WorkerTaskResponse(workerTask, compatibility("deadLetterWorkerTask", snapshot.map(run -> run.run().runId()).orElse(null)));
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

  private Map<String, Object> compatibility(String legacyTool, String runId) {
    return Map.of(
        "deprecated", true,
        "legacyTool", legacyTool,
        "canonicalTools", List.of("startDelegationRun", "appendDelegationRunEvent", "loadDelegationRun", "listDelegationRuns", "completeDelegationRun"),
        "delegationRunId", runId == null ? "" : runId,
        "notes", "Legacy orchestration tool mapped through compatibility adapter to codex delegation run."
    );
  }

  private Map<String, Object> stringProperty(String description) {
    return schemaFactory.stringProperty(description);
  }

  private Map<String, Object> booleanProperty(String description) {
    return schemaFactory.booleanProperty(description);
  }

  private Map<String, Object> arrayProperty(String description, Map<String, Object> itemSchema) {
    return schemaFactory.arrayProperty(description, itemSchema);
  }
}
