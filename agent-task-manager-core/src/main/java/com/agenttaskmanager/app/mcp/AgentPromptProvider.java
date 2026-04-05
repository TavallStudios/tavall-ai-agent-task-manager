package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.orchestration.PromptOutputGuidanceService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptArgument;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AgentPromptProvider implements McpPromptProvider {

  private final McpResultFactory mcpResultFactory;
  private final PromptOutputGuidanceService promptOutputGuidanceService;

  public AgentPromptProvider(
      McpResultFactory mcpResultFactory,
      PromptOutputGuidanceService promptOutputGuidanceService
  ) {
    this.mcpResultFactory = mcpResultFactory;
    this.promptOutputGuidanceService = promptOutputGuidanceService;
  }

  @Override
  public List<SyncPromptSpecification> promptSpecifications() {
    return List.of(
        prompt("overseerAgent", "Overseer prompt with task splitting, assignment, and fail-closed patch gating."),
        prompt("workerAgent", "Worker prompt with required check-ins, shared context usage, and artifact delivery."),
        prompt("cleanupAgent", "Cleanup prompt with continuous diff review and rework escalation.")
    );
  }

  private SyncPromptSpecification prompt(String name, String description) {
    Prompt prompt = new Prompt(
        name,
        name,
        description,
        List.of(new PromptArgument("taskId", "Target task identifier", true))
    );
    return new SyncPromptSpecification(prompt, (exchange, request) -> {
      String taskId = String.valueOf(request.arguments().getOrDefault("taskId", ""));
      return mcpResultFactory.promptResult(
          description,
          """
          Role: %s
          Task id: %s

          Deterministic execution policy:
          %s

          Memory policy:
          %s

          Tool combination patterns:
          %s

          Final response contract:
          %s

          Requirements:
          - follow AGENTS.md, RULES.md, and UNIVERSAL.md
          - use MCP tools instead of self-certifying work
          - use runHarnessToolBundle to assemble repository, state, and retrieval context before acting
          - let AgentTaskManager broker filesystem, ripgrep, and git in parallel instead of calling them one by one
          - repo-backed write runs that produce a diff must use planGitCommit, prepareGitBranch, and createGitCommit instead of raw shell git commands
          - expect harness transcript messages for memory lookup, Java symbol preload, tool policy, semantic sync, observed tool calls, and final git workflow outcome
          - do not use downstream git mutation tools such as git_commit, git_add, git_checkout, git_create_branch, or git_reset when AgentTaskManager workflow tools are available
          - when changing Java code, review task context, validation history, prior fixes, and the preloaded Java symbol context before editing
          - expect AgentTaskManager to compare post-edit Java contract deltas and require rework if signatures, visibility, inheritance, throws, or field types drift unexpectedly
          - expect local Spoon and ArchUnit validation to run through AgentTaskManager runtime approval after the worker process ends
          - treat Spoon and ArchUnit failures as structured remediation that must be fixed before approval
          - keep explicit check-ins and artifacts
          - require cleanup review plus validation before approval
          """.formatted(
              name,
              taskId,
              promptOutputGuidanceService.deterministicExecutionPolicy(),
              promptOutputGuidanceService.memoryPolicy(),
              promptOutputGuidanceService.toolCombinationPatterns(),
              promptOutputGuidanceService.finalResponseContract()
          )
      );
    });
  }
}
