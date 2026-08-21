package org.tavall.agent.architecture;

import org.tavall.agent.TavallAgent;
import org.tavall.agent.TavallAgentCapability;
import org.tavall.agent.TavallAgentInstructions;
import org.tavall.agent.TavallAgentKind;
import org.tavall.agent.TavallAgentProvider;
import org.tavall.dependency.annotations.DelegatesTo;

import java.util.Set;

/** Broad structural migration agent with explicit architecture mutation requirements. */
@DelegatesTo
public final class ArchitectureAgentProvider implements TavallAgentProvider {
    public static final String AGENT_ID = "architecture";

    @Override
    public TavallAgent agent() {
        return new TavallAgent(
                AGENT_ID,
                "Performs approved cross-module architecture migrations and structural repairs.",
                TavallAgentKind.WORK,
                TavallAgentInstructions.load(ArchitectureAgentProvider.class, "ROLE.md"),
                Set.of("repo_read", "repo_search", "repo_write", "git_status", "git_diff", "git_commit_checkpoint", "git_push", "ci_verify"),
                Set.of("github_list_prs", "github_inspect_pr", "github_update_pr"),
                Set.of(
                        TavallAgentCapability.FUNCTION_DISCOVERY,
                        TavallAgentCapability.REPOSITORY_READ,
                        TavallAgentCapability.REPOSITORY_WRITE,
                        TavallAgentCapability.GIT_CHECKPOINT,
                        TavallAgentCapability.PULL_REQUEST_READ,
                        TavallAgentCapability.PULL_REQUEST_WRITE,
                        TavallAgentCapability.LOCAL_CI,
                        TavallAgentCapability.ARCHITECTURE_MUTATION
                ),
                false,
                false
        );
    }
}
