package org.tavall.agent.documentation;

import org.tavall.agent.TavallAgent;
import org.tavall.agent.TavallAgentCapability;
import org.tavall.agent.TavallAgentInstructions;
import org.tavall.agent.TavallAgentKind;
import org.tavall.agent.TavallAgentProvider;
import org.tavall.dependency.annotations.DelegatesTo;

import java.util.Set;

/** Documentation and durable evidence agent. */
@DelegatesTo
public final class DocumentationAgentProvider implements TavallAgentProvider {
    public static final String AGENT_ID = "documentation";

    @Override
    public TavallAgent agent() {
        return new TavallAgent(
                AGENT_ID,
                "Updates owning documentation from accepted code, architecture decisions, and validation evidence.",
                TavallAgentKind.WORK,
                TavallAgentInstructions.load(DocumentationAgentProvider.class, "ROLE.md"),
                Set.of("repo_read", "repo_search", "repo_write", "git_status", "git_diff", "git_commit_checkpoint", "git_push"),
                Set.of("ci_verify", "github_inspect_pr", "github_update_pr"),
                Set.of(
                        TavallAgentCapability.FUNCTION_DISCOVERY,
                        TavallAgentCapability.REPOSITORY_READ,
                        TavallAgentCapability.REPOSITORY_WRITE,
                        TavallAgentCapability.GIT_CHECKPOINT,
                        TavallAgentCapability.PULL_REQUEST_READ,
                        TavallAgentCapability.PULL_REQUEST_WRITE,
                        TavallAgentCapability.DOCUMENTATION_WRITE
                ),
                false,
                false
        );
    }
}
