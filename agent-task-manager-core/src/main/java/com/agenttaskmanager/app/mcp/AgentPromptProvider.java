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
          - follow AGENTS.md and RULES.md
          - use MCP tools instead of self-certifying work
          - use runHarnessToolBundle to assemble repository, state, and retrieval context before acting
          - let the harness broker filesystem, ripgrep, and git in parallel instead of calling them one by one
          - when changing Java code, call loadCleanJavaTaskContext before editing, then use the java-context harness bundle, then run the clean Java harness before approval
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
