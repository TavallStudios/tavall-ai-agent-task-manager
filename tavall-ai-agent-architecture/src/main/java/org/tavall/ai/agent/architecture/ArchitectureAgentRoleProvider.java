package org.tavall.ai.agent.architecture;

import org.tavall.ai.agent.role.TavallAIAgentRole;
import org.tavall.ai.agent.role.TavallAIAgentRoleCapability;
import org.tavall.ai.agent.role.TavallAIAgentRoleInstructions;
import org.tavall.ai.agent.role.TavallAIAgentRoleKind;
import org.tavall.ai.agent.role.TavallAIAgentRoleProvider;

import java.util.Set;

/** Broad structural migration role with explicit architecture mutation authority. */
public final class ArchitectureAgentRoleProvider implements TavallAIAgentRoleProvider {
    public static final String ROLE_ID = "architecture";

    @Override
    public TavallAIAgentRole role() {
        return new TavallAIAgentRole(
                ROLE_ID,
                "Performs approved cross-module architecture migrations and structural repairs.",
                TavallAIAgentRoleKind.WORK,
                TavallAIAgentRoleInstructions.load(ArchitectureAgentRoleProvider.class, "ROLE.md"),
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
                        "github_list_prs",
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
                        TavallAIAgentRoleCapability.LOCAL_CI,
                        TavallAIAgentRoleCapability.ARCHITECTURE_MUTATION
                ),
                false,
                false
        );
    }
}
