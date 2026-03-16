package com.agenttaskmanager.app.harness.intake;

import java.util.List;
import java.util.Map;

public record ParentTaskRequest(
    String taskId,
    ParentTaskType type,
    String title,
    String description,
    String repoRef,
    String priority,
    String requestedBy,
    boolean requiresCleanupReview,
    boolean requiresIntegrationTests,
    boolean multiAgentEnabled,
    List<String> requestedWorkerTypes,
    List<String> changedFiles,
    String gitBase,
    String gitHead,
    Map<String, Object> codebaseInput,
    Map<String, Object> storedContextInput,
    Map<String, Object> ruleInput,
    Map<String, Object> liveDebugInput,
    Map<String, Object> metadata
) {

  public ParentTaskRequest {
    requestedWorkerTypes = requestedWorkerTypes == null ? List.of() : List.copyOf(requestedWorkerTypes);
    changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
    codebaseInput = codebaseInput == null ? Map.of() : Map.copyOf(codebaseInput);
    storedContextInput = storedContextInput == null ? Map.of() : Map.copyOf(storedContextInput);
    ruleInput = ruleInput == null ? Map.of() : Map.copyOf(ruleInput);
    liveDebugInput = liveDebugInput == null ? Map.of() : Map.copyOf(liveDebugInput);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
