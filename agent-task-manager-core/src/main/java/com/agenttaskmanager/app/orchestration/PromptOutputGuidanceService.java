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
        - use the configured MCP servers for repository inspection and semantic retrieval first
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
        - filesystem + ripgrep: use ripgrep to narrow files quickly, then open exact files with filesystem tooling to confirm behavior before changing anything
        - qdrant + filesystem/ripgrep: use semantic memory to form the initial hypothesis, then verify every important claim against the live repository
        - git + filesystem: inspect the current worktree before editing and review the final diff against the touched files before reporting completion
        - loadCleanJavaRules + runCleanJavaHarness: when the task changes Java code, load the rules before editing and run the deterministic harness after the diff is ready
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
