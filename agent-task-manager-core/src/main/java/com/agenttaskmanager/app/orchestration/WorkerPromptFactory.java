package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.model.orchestration.WorkerType;
import com.agenttaskmanager.app.model.orchestration.WorkerTask;
import org.springframework.stereotype.Component;

@Component
public class WorkerPromptFactory {

  private final PromptMemoryLookupService promptMemoryLookupService;
  private final ContextualToolPolicyService contextualToolPolicyService;
  private final PromptOutputGuidanceService promptOutputGuidanceService;

  public WorkerPromptFactory(
      PromptMemoryLookupService promptMemoryLookupService,
      ContextualToolPolicyService contextualToolPolicyService,
      PromptOutputGuidanceService promptOutputGuidanceService
  ) {
    this.promptMemoryLookupService = promptMemoryLookupService;
    this.contextualToolPolicyService = contextualToolPolicyService;
    this.promptOutputGuidanceService = promptOutputGuidanceService;
  }

  public String buildPrompt(String projectKey, WorkerTask workerTask) {
    return buildPrompt(
        projectKey,
        workerTask,
        promptMemoryLookupService.lookup(projectKey, buildQuery(workerTask)).section(),
        "No deterministic Java symbol context was preloaded."
    );
  }

  public String buildPrompt(String projectKey, WorkerTask workerTask, String memorySection) {
    return buildPrompt(projectKey, workerTask, memorySection, "No deterministic Java symbol context was preloaded.");
  }

  public String buildPrompt(String projectKey, WorkerTask workerTask, String memorySection, String javaSymbolSection) {
    return """
        Worker type: %s
        Role: %s
        Task title: %s

        Deterministic execution policy:
        %s

        Memory policy:
        %s

        Tool combination patterns:
        %s

        Contextual tool policy:
        %s

        Final response contract:
        %s

        Memory context:
        %s

        Java symbol context:
        %s

        Worker focus:
        %s

        Computer-use execution contract:
        %s

        Requirements:
        - follow AGENTS.md, RULES.md, UNIVERSAL.md, and ARCHITECTURE.md
        - produce a concrete artifact or diff
        - keep code changes scoped and architecture-safe
        - when the task changes Java code, load the deterministic clean Java task context before editing and use the staged harness feedback before approval
        - keep the work inside the assigned worker type instead of doing other workers' jobs
        - expect cleanup review and validation before approval
        """.formatted(
        workerTask.workerType().name(),
        workerTask.taskRole(),
        workerTask.title(),
        promptOutputGuidanceService.deterministicExecutionPolicy(),
        promptOutputGuidanceService.memoryPolicy(),
        promptOutputGuidanceService.toolCombinationPatterns(),
        contextualToolPolicyService.buildPolicy(
            "edit",
            workerTask.taskRole() + " " + workerTask.title() + " " + (workerTask.latestSummary() == null ? "" : workerTask.latestSummary()),
            true,
            true
        ),
        promptOutputGuidanceService.finalResponseContract(),
        memorySection == null || memorySection.isBlank() ? "No memory context was retrieved." : memorySection.strip(),
        javaSymbolSection == null || javaSymbolSection.isBlank()
            ? "No deterministic Java symbol context was preloaded."
            : javaSymbolSection.strip(),
        workerTask.workerType().promptFocus(),
        computerUseExecutionContract(workerTask)
    );
  }

  private static String buildQuery(WorkerTask workerTask) {
    String latestSummary = workerTask.latestSummary() == null ? "" : workerTask.latestSummary().strip();
    return (workerTask.workerType().name() + " " + workerTask.taskRole() + " " + workerTask.title() + " " + latestSummary).strip();
  }

  private String computerUseExecutionContract(WorkerTask workerTask) {
    if (workerTask.workerType() != WorkerType.COMPUTER_USE) {
      return "Use the normal repo and harness tool path for non-computer-use work.";
    }
    return """
        - acquire or reference a dedicated computer-use runner instead of the operator desktop
        - start a computer-use session with one of: hytale/launch-and-join-smoke, hytale/gameplay-assets-visible, hytale/chart-start-stable, hytale/note-hit-interaction
        - drive launch, join, UI, gameplay-asset, and note-input checkpoints through the computer-use runner
        - use the persisted scenarioDefinition metadata to track required steps, artifacts, and pass-fail gates
        - store screenshots or runner artifacts for every failed gate
        - treat the task as incomplete unless the session proves the expected visual or gameplay markers
        """;
  }
}
