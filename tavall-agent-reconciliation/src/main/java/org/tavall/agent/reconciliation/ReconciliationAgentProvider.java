package org.tavall.agent.reconciliation;

import org.tavall.agent.TavallAgent;
import org.tavall.agent.TavallAgentCapability;
import org.tavall.agent.TavallAgentInstructions;
import org.tavall.agent.TavallAgentKind;
import org.tavall.agent.TavallAgentProvider;

import java.util.Set;

/** Current-main and pull-request graph reconciliation agent. */
public final class ReconciliationAgentProvider implements TavallAgentProvider {
    public static final String AGENT_ID = "reconciliation";

    @Override
    public TavallAgent agent() {
        return new TavallAgent(
                AGENT_ID,
                "Reconciles PR topology, current-main drift, migration debt, ownership, and stale branches.",
                TavallAgentKind.WORK,
                TavallAgentInstructions.load(ReconciliationAgentProvider.class, "ROLE.md"),
                Set.of("github_list_prs", "github_inspect_pr", "repo_read", "repo_search", "git_status", "git_diff", "git_commit_checkpoint", "git_push", "github_update_pr", "ci_verify"),
                Set.of("git_compare", "git_rebase", "github_close_pr"),
                Set.of(
                        TavallAgentCapability.FUNCTION_DISCOVERY,
                        TavallAgentCapability.REPOSITORY_READ,
                        TavallAgentCapability.REPOSITORY_WRITE,
                        TavallAgentCapability.GIT_CHECKPOINT,
                        TavallAgentCapability.PULL_REQUEST_READ,
                        TavallAgentCapability.PULL_REQUEST_WRITE,
                        TavallAgentCapability.LOCAL_CI
                ),
                false,
                false
        );
    }
}
