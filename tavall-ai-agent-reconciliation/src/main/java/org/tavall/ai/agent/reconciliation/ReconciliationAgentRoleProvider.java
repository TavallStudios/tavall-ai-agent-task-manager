package org.tavall.ai.agent.reconciliation;

import org.tavall.ai.agent.role.TavallAIAgentRole;
import org.tavall.ai.agent.role.TavallAIAgentRoleCapability;
import org.tavall.ai.agent.role.TavallAIAgentRoleInstructions;
import org.tavall.ai.agent.role.TavallAIAgentRoleKind;
import org.tavall.ai.agent.role.TavallAIAgentRoleProvider;

import java.util.Set;

/** Current-main and pull-request graph reconciliation role. */
public final class ReconciliationAgentRoleProvider implements TavallAIAgentRoleProvider {
    public static final String ROLE_ID = "reconciliation";

    @Override
    public TavallAIAgentRole role() {
        return new TavallAIAgentRole(
                ROLE_ID,
                "Reconciles PR topology, current-main drift, migration debt, ownership, and stale branches.",
                TavallAIAgentRoleKind.WORK,
                TavallAIAgentRoleInstructions.load(ReconciliationAgentRoleProvider.class, "ROLE.md"),
                Set.of(
                        "github_list_prs",
                        "github_inspect_pr",
                        "repo_read",
                        "repo_search",
                        "git_status",
                        "git_diff",
                        "git_commit_checkpoint",
                        "git_push",
                        "github_update_pr",
                        "ci_verify"
                ),
                Set.of(
                        "git_compare",
                        "git_rebase",
                        "github_close_pr"
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
