package com.agenttaskmanager.app.web;

import com.agenttaskmanager.app.orchestration.CleanupReviewService;
import com.agenttaskmanager.app.orchestration.OverseerOrchestrationService;
import com.agenttaskmanager.app.orchestration.TaskPoolService;
import com.agenttaskmanager.app.orchestration.WorkerLifecycleService;
import com.agenttaskmanager.app.model.orchestration.CleanupReviewResult;
import com.agenttaskmanager.app.model.orchestration.OverseerTaskBatch;
import com.agenttaskmanager.app.model.orchestration.TaskAssignment;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.model.orchestration.WorkerCheckIn;
import com.agenttaskmanager.app.model.orchestration.WorkerTask;
import com.agenttaskmanager.app.model.orchestration.WorkerTransportKind;
import com.agenttaskmanager.app.validation.ValidationPipelineService;
import com.agenttaskmanager.app.model.validation.ValidationReport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/orchestration")
public class OrchestrationApiController {

  private final TaskPoolService taskPoolService;
  private final WorkerLifecycleService workerLifecycleService;
  private final CleanupReviewService cleanupReviewService;
  private final OverseerOrchestrationService overseerOrchestrationService;
  private final ValidationPipelineService validationPipelineService;

  public OrchestrationApiController(
      TaskPoolService taskPoolService,
      WorkerLifecycleService workerLifecycleService,
      CleanupReviewService cleanupReviewService,
      OverseerOrchestrationService overseerOrchestrationService,
      ValidationPipelineService validationPipelineService
  ) {
    this.taskPoolService = taskPoolService;
    this.workerLifecycleService = workerLifecycleService;
    this.cleanupReviewService = cleanupReviewService;
    this.overseerOrchestrationService = overseerOrchestrationService;
    this.validationPipelineService = validationPipelineService;
  }

  @PostMapping("/batches")
  public CreateBatchResponse createBatch(@Valid @RequestBody CreateBatchRequest request) {
    OverseerTaskBatch batch = overseerOrchestrationService.createTaskBatch(
        request.projectKey(),
        request.sourceRepo(),
        request.title(),
        request.multiAgentEnabled(),
        request.workerRoles()
    );
    return new CreateBatchResponse(batch);
  }

  @GetMapping("/batches/{taskId}/workers")
  public WorkerTaskListResponse workerTasks(@PathVariable String taskId) {
    return new WorkerTaskListResponse(taskPoolService.listWorkerTasks(taskId));
  }

  @PostMapping("/batches/{taskId}/assignments")
  public AssignmentResponse assignNextWorker(
      @PathVariable String taskId,
      @Valid @RequestBody AssignWorkerRequest request
  ) {
    TaskAssignment assignment = overseerOrchestrationService.assignNextWorkerTask(
        taskId,
        request.agentId(),
        request.transportKind(),
        request.sessionId()
    );
    return new AssignmentResponse(assignment);
  }

  @PostMapping("/workers/{workerTaskId}/check-ins")
  public WorkerCheckInResponse submitCheckIn(
      @PathVariable String workerTaskId,
      @Valid @RequestBody WorkerCheckInRequest request
  ) {
    WorkerCheckIn checkIn = workerLifecycleService.submitWorkerCheckIn(
        workerTaskId,
        request.taskId(),
        request.agentId(),
        request.status(),
        request.summary(),
        request.details()
    );
    return new WorkerCheckInResponse(checkIn);
  }

  @PostMapping("/workers/{workerTaskId}/complete")
  public WorkerTaskResponse completeWorker(
      @PathVariable String workerTaskId,
      @Valid @RequestBody WorkerUpdateRequest request
  ) {
    return new WorkerTaskResponse(taskPoolService.completeWorkerTask(workerTaskId, request.summary()));
  }

  @PostMapping("/workers/{workerTaskId}/fail")
  public WorkerTaskResponse failWorker(
      @PathVariable String workerTaskId,
      @Valid @RequestBody WorkerUpdateRequest request
  ) {
    return new WorkerTaskResponse(taskPoolService.failWorkerTask(workerTaskId, request.summary()));
  }

  @PostMapping("/reviews/{cleanupReviewId}/run")
  public CleanupReviewResponse runCleanupReview(@PathVariable String cleanupReviewId) {
    CleanupReviewResult result = cleanupReviewService.runCleanupDiffReview(cleanupReviewId);
    return new CleanupReviewResponse(result);
  }

  @PostMapping("/batches/{taskId}/validate")
  public ValidationReportResponse validateBatch(
      @PathVariable String taskId,
      @Valid @RequestBody ValidationRequest request
  ) {
    ValidationReport report = validationPipelineService.runValidationPipeline(
        taskId,
        request.workerTaskId(),
        Path.of(request.repoPath())
    );
    return new ValidationReportResponse(report);
  }

  public record CreateBatchRequest(
      @NotBlank String projectKey,
      @NotBlank String sourceRepo,
      @NotBlank String title,
      boolean multiAgentEnabled,
      List<String> workerRoles
  ) {
  }

  public record AssignWorkerRequest(
      @NotBlank String agentId,
      @NotBlank String sessionId,
      WorkerTransportKind transportKind
  ) {
  }

  public record WorkerCheckInRequest(
      @NotBlank String taskId,
      @NotBlank String agentId,
      TaskLifecycleStatus status,
      @NotBlank String summary,
      Map<String, Object> details
  ) {
  }

  public record WorkerUpdateRequest(@NotBlank String summary) {
  }

  public record ValidationRequest(String workerTaskId, @NotBlank String repoPath) {
  }

  public record CreateBatchResponse(OverseerTaskBatch batch) {
  }

  public record WorkerTaskListResponse(List<WorkerTask> items) {
  }

  public record AssignmentResponse(TaskAssignment assignment) {
  }

  public record WorkerCheckInResponse(WorkerCheckIn checkIn) {
  }

  public record WorkerTaskResponse(WorkerTask workerTask) {
  }

  public record CleanupReviewResponse(CleanupReviewResult cleanupReview) {
  }

  public record ValidationReportResponse(ValidationReport report) {
  }
}
