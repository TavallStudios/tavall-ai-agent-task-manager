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
                "Reconciles staging/PR topology, current-main drift, migration debt, ownership, and stale branches.",
                TavallAgentKind.WORK,
                TavallAgentInstructions.load(ReconciliationAgentProvider.class, "ROLE.md"),
                Set.of(
                        "github_list_prs", "github_inspect_pr", "repo_read", "repo_search", "git_status", "git_diff",
                        "git_commit_checkpoint", "git_push", "github_update_pr", "ci_verify",
                        "repository_staging_discover", "repository_staging_inspect_graph", "repository_staging_resolve_base",
                        "repository_staging_validate", "repository_staging_ensure", "repository_staging_attach",
                        "repository_staging_set_state",
                        "cloud_dev_lane_list", "cloud_dev_lane_inspect", "cloud_dev_environment_list",
                        "cloud_dev_environment_inspect", "cloud_dev_environment_resolve",
                        "cloud_dev_environment_components", "cloud_dev_environment_operations",
                        "cloud_dev_environment_validations"
                ),
                Set.of(
                        "git_compare", "git_rebase", "github_close_pr",
                        "repository_staging_prepare_promotion"
                ),
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
