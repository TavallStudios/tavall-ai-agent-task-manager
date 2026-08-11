package org.tavall.ai.agent.review;

import org.tavall.ai.agent.role.TavallAIAgentRole;
import org.tavall.ai.agent.role.TavallAIAgentRoleCapability;
import org.tavall.ai.agent.role.TavallAIAgentRoleInstructions;
import org.tavall.ai.agent.role.TavallAIAgentRoleKind;
import org.tavall.ai.agent.role.TavallAIAgentRoleProvider;

import java.util.Set;

/** Independent exact-head repository and pull-request review role. */
public final class ReviewAgentRoleProvider implements TavallAIAgentRoleProvider {
    public static final String ROLE_ID = "review";

    @Override
    public TavallAIAgentRole role() {
        return new TavallAIAgentRole(
                ROLE_ID,
                "Independently reviews an exact head for correctness, regressions, architecture, and evidence gaps.",
                TavallAIAgentRoleKind.WORK,
                TavallAIAgentRoleInstructions.load(ReviewAgentRoleProvider.class, "ROLE.md"),
                Set.of(
                        "repo_read",
                        "repo_search",
                        "git_diff",
                        "github_inspect_pr",
                        "ci_verify"
                ),
                Set.of(
                        "github_review_pr",
                        "github_list_review_threads"
                ),
                Set.of(
                        TavallAIAgentRoleCapability.FUNCTION_DISCOVERY,
                        TavallAIAgentRoleCapability.REPOSITORY_READ,
                        TavallAIAgentRoleCapability.PULL_REQUEST_READ,
                        TavallAIAgentRoleCapability.LOCAL_CI,
                        TavallAIAgentRoleCapability.REVIEW_DECISION
                ),
                false,
                false
        );
    }
}
