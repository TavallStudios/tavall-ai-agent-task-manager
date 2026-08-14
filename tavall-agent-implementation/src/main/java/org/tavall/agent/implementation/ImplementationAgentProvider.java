package org.tavall.agent.implementation;

import org.tavall.agent.TavallAgent;
import org.tavall.agent.TavallAgentCapability;
import org.tavall.agent.TavallAgentInstructions;
import org.tavall.agent.TavallAgentKind;
import org.tavall.agent.TavallAgentProvider;

import java.util.Set;

/** Bounded repository implementation agent. */
public final class ImplementationAgentProvider implements TavallAgentProvider {
    public static final String AGENT_ID = "implementation";

    @Override
    public TavallAgent agent() {
        return new TavallAgent(
                AGENT_ID,
                "Implements one bounded acceptance unit, tests it, and pushes durable checkpoints.",
                TavallAgentKind.WORK,
                TavallAgentInstructions.load(ImplementationAgentProvider.class, "ROLE.md"),
                Set.of(
                        "repo_read", "repo_search", "repo_write", "git_status", "git_diff",
                        "git_commit_checkpoint", "git_push", "ci_verify",
                        "repository_staging_discover", "repository_staging_resolve_base", "repository_staging_validate"
                ),
                Set.of("github_inspect_pr", "github_update_pr", "repository_staging_inspect_graph"),
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
