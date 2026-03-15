package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.model.orchestration.WorkerTask;
import org.springframework.stereotype.Component;

@Component
public class WorkerPromptFactory {

  private final PromptMemoryLookupService promptMemoryLookupService;

  public WorkerPromptFactory(PromptMemoryLookupService promptMemoryLookupService) {
    this.promptMemoryLookupService = promptMemoryLookupService;
  }

  public String buildPrompt(String projectKey, WorkerTask workerTask) {
    return """
        Role: %s
        Task title: %s

        Memory policy:
        - check memory context first before acting on the task
        - keep checking memory while evaluating the prompt and before the final response
        - if memory conflicts with fresher repository evidence, prefer repository evidence and call out the conflict

        Memory context:
        %s

        Requirements:
        - follow AGENTS.md, RULES.md, and ARCHITECTURE.md
        - produce a concrete artifact or diff
        - keep code changes scoped and architecture-safe
        - expect cleanup review and validation before approval
        """.formatted(
        workerTask.taskRole(),
        workerTask.title(),
        promptMemoryLookupService.lookup(projectKey, buildQuery(workerTask)).section()
    );
  }

  private static String buildQuery(WorkerTask workerTask) {
    String latestSummary = workerTask.latestSummary() == null ? "" : workerTask.latestSummary().strip();
    return (workerTask.taskRole() + " " + workerTask.title() + " " + latestSummary).strip();
  }
}
