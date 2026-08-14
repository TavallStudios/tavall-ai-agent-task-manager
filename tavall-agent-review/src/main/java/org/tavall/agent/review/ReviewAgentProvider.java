package org.tavall.agent.review;

import org.tavall.agent.TavallAgent;
import org.tavall.agent.TavallAgentCapability;
import org.tavall.agent.TavallAgentInstructions;
import org.tavall.agent.TavallAgentKind;
import org.tavall.agent.TavallAgentProvider;

import java.util.Set;

/** Independent exact-head repository and pull-request review agent. */
public final class ReviewAgentProvider implements TavallAgentProvider {
    public static final String AGENT_ID = "review";

    @Override
    public TavallAgent agent() {
        return new TavallAgent(
                AGENT_ID,
                "Independently reviews an exact head for correctness, regressions, architecture, and evidence gaps.",
                TavallAgentKind.WORK,
                TavallAgentInstructions.load(ReviewAgentProvider.class, "ROLE.md"),
                Set.of(
                        "repo_read", "repo_search", "git_diff", "github_inspect_pr", "ci_verify",
                        "repository_staging_discover", "repository_staging_inspect_graph", "repository_staging_validate"
                ),
                Set.of(
                        "github_review_pr", "github_list_review_threads",
                        "repository_staging_resolve_base", "repository_staging_prepare_promotion"
                ),
                Set.of(
                        TavallAgentCapability.FUNCTION_DISCOVERY,
                        TavallAgentCapability.REPOSITORY_READ,
                        TavallAgentCapability.PULL_REQUEST_READ,
                        TavallAgentCapability.LOCAL_CI,
                        TavallAgentCapability.REVIEW_DECISION
                ),
                false,
                false
        );
    }
}
