package org.tavall.ai.app.harness.cleanjava;

import org.tavall.ai.app.harness.state.HarnessStateSnapshot;
import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;
import org.tavall.ai.app.model.validation.ValidationReport;
import java.util.List;
import java.util.Map;

public record CleanJavaTaskContext(
    String taskId,
    String workerTaskId,
    String projectKey,
    String repoPath,
    String requestedTask,
    String queryText,
    List<String> relevantFiles,
    List<Map<String, Object>> relevantDiffs,
    String rules,
    String examples,
    String architecture,
    List<RetrievedSemanticContext> similarFixes,
    Map<String, Object> packageDependencyMap,
    List<ValidationReport> validationHistory,
    List<Map<String, Object>> relevantArtifacts,
    HarnessStateSnapshot harnessState
) {
}

