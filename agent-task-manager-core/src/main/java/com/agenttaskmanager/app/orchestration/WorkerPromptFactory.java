package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.model.orchestration.WorkerTask;
import org.springframework.stereotype.Component;

@Component
public class WorkerPromptFactory {

  private final PromptMemoryLookupService promptMemoryLookupService;
  private final PromptOutputGuidanceService promptOutputGuidanceService;

  public WorkerPromptFactory(
      PromptMemoryLookupService promptMemoryLookupService,
      PromptOutputGuidanceService promptOutputGuidanceService
  ) {
    this.promptMemoryLookupService = promptMemoryLookupService;
    this.promptOutputGuidanceService = promptOutputGuidanceService;
  }

  public String buildPrompt(String projectKey, WorkerTask workerTask) {
    return """
        Role: %s
        Task title: %s

        Deterministic execution policy:
        %s

        Memory policy:
        %s

        Tool combination patterns:
        %s

        Final response contract:
        %s

        Memory context:
        %s

        Requirements:
        - follow AGENTS.md, RULES.md, and ARCHITECTURE.md
        - produce a concrete artifact or diff
        - keep code changes scoped and architecture-safe
        - use the clean Java rules plus harness when the task changes Java code
        - expect cleanup review and validation before approval
        """.formatted(
        workerTask.taskRole(),
        workerTask.title(),
        promptOutputGuidanceService.deterministicExecutionPolicy(),
        promptOutputGuidanceService.memoryPolicy(),
        promptOutputGuidanceService.toolCombinationPatterns(),
        promptOutputGuidanceService.finalResponseContract(),
        promptMemoryLookupService.lookup(projectKey, buildQuery(workerTask)).section()
    );
  }

  private static String buildQuery(WorkerTask workerTask) {
    String latestSummary = workerTask.latestSummary() == null ? "" : workerTask.latestSummary().strip();
    return (workerTask.taskRole() + " " + workerTask.title() + " " + latestSummary).strip();
  }
}
