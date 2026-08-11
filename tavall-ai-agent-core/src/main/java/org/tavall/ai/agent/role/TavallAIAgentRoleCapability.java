package org.tavall.ai.agent.role;

/**
 * Coarse role capabilities used for policy, placement, and operator visibility.
 *
 * <p>These values describe what a role is designed to request. They are never authority by
 * themselves. Function Catalog views and Tavall Cloud remain responsible for the callable and
 * machine-level authority granted to a concrete execution.</p>
 */
public enum TavallAIAgentRoleCapability {
    FUNCTION_DISCOVERY,
    REPOSITORY_READ,
    REPOSITORY_WRITE,
    GIT_CHECKPOINT,
    PULL_REQUEST_READ,
    PULL_REQUEST_WRITE,
    LOCAL_CI,
    RUNTIME_E2E,
    DISTRIBUTED_SCHEDULING,
    SUBAGENT_ORCHESTRATION,
    ARCHITECTURE_MUTATION,
    DOCUMENTATION_WRITE,
    REVIEW_DECISION
}
