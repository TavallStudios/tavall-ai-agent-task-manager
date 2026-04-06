package org.tavall.ai.app.orchestration;

import org.springframework.stereotype.Service;

@Service
public class PromptOutputGuidanceService {

  /**
   * Provides the shared deterministic execution policy that all prompt entry points should enforce.
   */
  public String deterministicExecutionPolicy() {
    return """
        - tool access is configured by AgentTaskManager before the run starts
        - tavall-ai is the primary MCP surface for Codex worker runs
        - repository inspection should flow through the brokered repo-context tools before reaching for fallback local execution
        - repo-backed write runs that produce a git diff must finish through the first-party git workflow MCP tools instead of raw shell git mutation
        - clean Java validation is bundled locally and runs through AgentTaskManager runtime validation instead of a separate harness server
        - harness preferences: DI preset `%s`, language preset `%s`, custom DI descriptor `%s`
        - repo-backed Java write runs preload deterministic Java symbol context before editing and compare contract deltas before approval
        - workers do not self-approve; they must pass cleanup review, staged validation, and approval gates
        - on native Windows outside WSL, do not use shell_command; use runHarnessToolBundle(repo-context) and first-party MCP tools instead
        - treat direct shell searching as fallback-only when the MCP tools cannot satisfy the operation
        - verify repository state before claiming that work is complete
        """.formatted(
        harnessPreference("AGENTTASKMANAGER_HARNESS_DI_PRESET", "service-loader"),
        harnessPreference("AGENTTASKMANAGER_HARNESS_LANGUAGE_PRESET", "java"),
        harnessPreference("AGENTTASKMANAGER_HARNESS_CUSTOM_DI_DESCRIPTOR", "none")
    );
  }

  /**
   * Provides the shared memory handling policy for prompt generation.
   */
  public String memoryPolicy() {
    return """
        - review the memory context before analyzing the user request
        - keep that memory in working context while planning and while checking the prompt
        - re-check the memory context before the final response
        - if memory conflicts with fresher repository evidence, prefer repository evidence and call out the conflict
        """;
  }

  /**
   * Provides the standard tool-combination patterns that improve speed and output quality.
   */
  public String toolCombinationPatterns() {
    return """
        - runHarnessToolBundle(worker-context): load shared task state, retrieval context, git status, filesystem listing, and search results in one brokered call before editing
        - runHarnessToolBundle(repo-context): fan out filesystem, ripgrep, and git calls in parallel through the central MCP instead of chaining them from Codex
        - planGitCommit + prepareGitBranch + createGitCommit: keep branch naming deterministic and collapse each diff-producing repo-backed prompt into exactly one first-party git workflow commit with a verbose body
        - loadTaskContext + loadValidationHistory + searchPriorFixes: gather current task state, prior validation, and related Java fixes before editing
        - deterministic Java symbol harness: preload changed-class and neighbor summaries before editing, then compare post-edit contract deltas before approval
        - local clean Java validation runs after worker execution: Spoon source-shape checks first, then ArchUnit architecture checks, then cycle feedback before approval
        - use direct shell search only when the brokered harness bundle cannot satisfy the operation
        """;
  }

  /**
   * Provides the response format contract that keeps agent outputs high-signal and verifiable.
   */
  public String finalResponseContract() {
    return """
        - state what changed or what was found before background detail
        - cite the exact files, tools, validations, or diffs that support the conclusion
        - report verification status explicitly, including tests or harnesses that were not run
        - call out remaining risks, blockers, or unverified assumptions instead of implying certainty
        - end with explicit next steps; if no follow-up is needed, say "Next Steps: none"
        """;
  }

  private String harnessPreference(String key, String fallback) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.strip();
  }
}


