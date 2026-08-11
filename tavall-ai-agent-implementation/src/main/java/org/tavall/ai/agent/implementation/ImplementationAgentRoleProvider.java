package org.tavall.ai.agent.implementation;

import org.tavall.ai.agent.role.TavallAIAgentRole;
import org.tavall.ai.agent.role.TavallAIAgentRoleCapability;
import org.tavall.ai.agent.role.TavallAIAgentRoleInstructions;
import org.tavall.ai.agent.role.TavallAIAgentRoleKind;
import org.tavall.ai.agent.role.TavallAIAgentRoleProvider;

import java.util.Set;

/** Bounded repository implementation role. */
public final class ImplementationAgentRoleProvider implements TavallAIAgentRoleProvider {
    public static final String ROLE_ID = "implementation";

    @Override
    public TavallAIAgentRole role() {
        return new TavallAIAgentRole(
                ROLE_ID,
                "Implements one bounded acceptance unit, tests it, and pushes durable checkpoints.",
                TavallAIAgentRoleKind.WORK,
                TavallAIAgentRoleInstructions.load(ImplementationAgentRoleProvider.class, "ROLE.md"),
                Set.of(
                        "repo_read",
                        "repo_search",
                        "repo_write",
                        "git_status",
                        "git_diff",
                        "git_commit_checkpoint",
                        "git_push",
                        "ci_verify"
                ),
                Set.of(
                        "github_inspect_pr",
                        "github_update_pr"
                ),
                Set.of(
                        TavallAIAgentRoleCapability.FUNCTION_DISCOVERY,
                        TavallAIAgentRoleCapability.REPOSITORY_READ,
                        TavallAIAgentRoleCapability.REPOSITORY_WRITE,
                        TavallAIAgentRoleCapability.GIT_CHECKPOINT,
                        TavallAIAgentRoleCapability.PULL_REQUEST_READ,
                        TavallAIAgentRoleCapability.PULL_REQUEST_WRITE,
                        TavallAIAgentRoleCapability.LOCAL_CI
                ),
                false,
                false
        );
    }
}
