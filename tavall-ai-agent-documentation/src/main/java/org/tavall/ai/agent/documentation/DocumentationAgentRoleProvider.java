package org.tavall.ai.agent.documentation;

import org.tavall.ai.agent.role.TavallAIAgentRole;
import org.tavall.ai.agent.role.TavallAIAgentRoleCapability;
import org.tavall.ai.agent.role.TavallAIAgentRoleInstructions;
import org.tavall.ai.agent.role.TavallAIAgentRoleKind;
import org.tavall.ai.agent.role.TavallAIAgentRoleProvider;

import java.util.Set;

/** Documentation and durable evidence role. */
public final class DocumentationAgentRoleProvider implements TavallAIAgentRoleProvider {
    public static final String ROLE_ID = "documentation";

    @Override
    public TavallAIAgentRole role() {
        return new TavallAIAgentRole(
                ROLE_ID,
                "Updates owning documentation from accepted code, architecture decisions, and validation evidence.",
                TavallAIAgentRoleKind.WORK,
                TavallAIAgentRoleInstructions.load(DocumentationAgentRoleProvider.class, "ROLE.md"),
                Set.of(
                        "repo_read",
                        "repo_search",
                        "repo_write",
                        "git_status",
                        "git_diff",
                        "git_commit_checkpoint",
                        "git_push"
                ),
                Set.of(
                        "ci_verify",
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
                        TavallAIAgentRoleCapability.DOCUMENTATION_WRITE
                ),
                false,
                false
        );
    }
}
