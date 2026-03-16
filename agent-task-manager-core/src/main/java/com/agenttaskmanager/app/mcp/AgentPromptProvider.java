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
          - combine qdrant memory with filesystem or ripgrep verification before acting
          - use git plus file inspection to confirm the final scope before reporting completion
          - when changing Java code, load clean Java rules before editing and run the clean Java harness before approval
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
