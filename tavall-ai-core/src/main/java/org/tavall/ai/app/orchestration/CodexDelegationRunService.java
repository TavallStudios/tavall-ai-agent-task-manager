package org.tavall.ai.app.orchestration;

import org.tavall.ai.app.model.orchestration.CodexDelegationRun;
import org.tavall.ai.app.model.orchestration.CodexDelegationRunSnapshot;
import org.tavall.ai.app.model.orchestration.CodexDelegationStep;
import org.tavall.ai.app.model.orchestration.TaskLifecycleStatus;
import org.tavall.ai.app.persistence.postgres.CodexDelegationRunRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CodexDelegationRunService {

  private final CodexDelegationRunRepository repository;

  public CodexDelegationRunService(CodexDelegationRunRepository repository) {
    this.repository = repository;
  }

  public CodexDelegationRunSnapshot startRun(
      String taskId,
      String projectKey,
      String repoPath,
      String title,
      Map<String, Object> metadata
  ) {
    CodexDelegationRun run = repository.createRun(
        taskId,
        projectKey,
        repoPath,
        title == null || title.isBlank() ? "Codex delegation run" : title.strip(),
        TaskLifecycleStatus.RUNNING,
        "Delegation run started.",
        metadata == null ? Map.of() : metadata
    );
    repository.appendStep(
        run.runId(),
        "spawn-parent-run",
        TaskLifecycleStatus.RUNNING,
        "Spawned parent Codex run.",
        runContextDetails(run.taskId(), run.projectKey(), run.repoPath())
    );
    return loadRun(run.runId());
  }

  public CodexDelegationRunSnapshot appendEvent(
      String runId,
      String eventType,
      TaskLifecycleStatus status,
      String summary,
      Map<String, Object> details
  ) {
    repository.appendStep(
        runId,
        eventType == null || eventType.isBlank() ? "event" : eventType.strip(),
        status == null ? TaskLifecycleStatus.CHECKED_IN : status,
        summary == null ? "" : summary,
        details == null ? Map.of() : details
    );
    return loadRun(runId);
  }

  public CodexDelegationRunSnapshot completeRun(
      String runId,
      TaskLifecycleStatus status,
      String summary,
      Map<String, Object> details
  ) {
    TaskLifecycleStatus terminalStatus = status == null ? TaskLifecycleStatus.COMPLETED : status;
    String normalizedSummary = summary == null || summary.isBlank()
        ? "Delegation run completed."
        : summary.strip();
    repository.appendStep(
        runId,
        terminalStatus == TaskLifecycleStatus.COMPLETED ? "result" : "failure",
        terminalStatus,
        normalizedSummary,
        details == null ? Map.of() : details
    );
    CodexDelegationRun existingRun = repository.getRun(runId);
    Map<String, Object> mergedMetadata = mergeMetadata(existingRun.metadata(), details);
    repository.updateRunStatus(runId, terminalStatus, normalizedSummary, mergedMetadata);
    return loadRun(runId);
  }

  public CodexDelegationRunSnapshot loadRun(String runId) {
    CodexDelegationRun run = repository.getRun(runId);
    List<CodexDelegationStep> steps = repository.listSteps(runId);
    return new CodexDelegationRunSnapshot(run, steps);
  }

  public Optional<CodexDelegationRunSnapshot> findLatestByTaskId(String taskId) {
    return repository.findLatestByTaskId(taskId)
        .map(run -> new CodexDelegationRunSnapshot(run, repository.listSteps(run.runId())));
  }

  public List<CodexDelegationRun> listRuns(int limit, String status) {
    int normalizedLimit = Math.max(1, Math.min(limit, 100));
    return repository.listRuns(normalizedLimit, status);
  }

  public Optional<CodexDelegationRunSnapshot> appendEventByTaskId(
      String taskId,
      String eventType,
      TaskLifecycleStatus status,
      String summary,
      Map<String, Object> details
  ) {
    Optional<CodexDelegationRunSnapshot> snapshot = findLatestByTaskId(taskId);
    if (snapshot.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(appendEvent(
        snapshot.get().run().runId(),
        eventType,
        status,
        summary,
        details
    ));
  }

  private Map<String, Object> mergeMetadata(Map<String, Object> existing, Map<String, Object> update) {
    Map<String, Object> merged = new LinkedHashMap<>();
    if (existing != null) {
      merged.putAll(existing);
    }
    if (update != null) {
      merged.putAll(update);
    }
    return merged;
  }

  private Map<String, Object> runContextDetails(String taskId, String projectKey, String repoPath) {
    Map<String, Object> details = new LinkedHashMap<>();
    if (taskId != null && !taskId.isBlank()) {
      details.put("taskId", taskId);
    }
    if (projectKey != null && !projectKey.isBlank()) {
      details.put("projectKey", projectKey);
    }
    if (repoPath != null && !repoPath.isBlank()) {
      details.put("repoPath", repoPath);
    }
    return details;
  }
}

