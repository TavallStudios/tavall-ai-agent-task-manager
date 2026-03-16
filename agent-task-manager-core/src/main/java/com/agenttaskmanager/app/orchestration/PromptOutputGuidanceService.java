package com.agenttaskmanager.app.orchestration;

import org.springframework.stereotype.Service;

@Service
public class PromptOutputGuidanceService {

  /**
   * Provides the shared deterministic execution policy that all prompt entry points should enforce.
   */
  public String deterministicExecutionPolicy() {
    return """
        - tool access is configured by AgentTaskManager before the run starts
        - clean-java-harness is the primary MCP surface for Codex worker runs
        - the harness builds task context before code is drafted, including rules, examples, retrieval hits, package dependencies, and validation history
        - use the harness bundle tools to broker repository inspection and retrieval before reaching for individual tool calls
        - workers do not self-approve; they must pass cleanup review, staged validation, and approval gates
        - treat direct shell searching as fallback-only when the MCP tools cannot satisfy the operation
        - verify repository state before claiming that work is complete
        """;
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
        - runHarnessToolBundle(repo-context): fan out filesystem, ripgrep, and git calls in parallel on the harness server instead of chaining them from Codex
        - loadCleanJavaTaskContext + runHarnessToolBundle(java-context): build deterministic Java context with rules, examples, prior fixes, package dependencies, and repo state before editing
        - runCleanJavaHarness: run Spoon source-shape checks first, then ArchUnit architecture checks, then cycle feedback before approval
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
        """;
  }
}
