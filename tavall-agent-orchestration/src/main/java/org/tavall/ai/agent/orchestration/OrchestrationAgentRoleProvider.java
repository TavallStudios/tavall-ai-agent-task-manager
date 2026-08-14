package org.tavall.ai.agent.orchestration;

import org.tavall.ai.agent.role.TavallAIAgentRole;
import org.tavall.ai.agent.role.TavallAIAgentRoleCapability;
import org.tavall.ai.agent.role.TavallAIAgentRoleInstructions;
import org.tavall.ai.agent.role.TavallAIAgentRoleKind;
import org.tavall.ai.agent.role.TavallAIAgentRoleProvider;

import java.util.Set;

/** Session-level control role that composes specialized Tavall agents inside one Codex session. */
public final class OrchestrationAgentRoleProvider implements TavallAIAgentRoleProvider {
    public static final String ROLE_ID = "orchestration";

    @Override
    public TavallAIAgentRole role() {
        return new TavallAIAgentRole(
                ROLE_ID,
                "Coordinates specialized Tavall role agents and subagents inside one owning Codex session.",
                TavallAIAgentRoleKind.CONTROL,
                TavallAIAgentRoleInstructions.load(OrchestrationAgentRoleProvider.class, "ROLE.md"),
                Set.of(
                        "ai_spawn_subagent",
                        "ai_inspect_subagent",
                        "ai_join_subagent"
                ),
                Set.of(
                        "ai_request_distributed_job",
                        "ai_inspect_job",
                        "ci_verify",
                        "github_inspect_pr"
                ),
                Set.of(
                        TavallAIAgentRoleCapability.FUNCTION_DISCOVERY,
                        TavallAIAgentRoleCapability.REPOSITORY_READ,
                        TavallAIAgentRoleCapability.PULL_REQUEST_READ,
                        TavallAIAgentRoleCapability.LOCAL_CI,
                        TavallAIAgentRoleCapability.DISTRIBUTED_SCHEDULING,
                        TavallAIAgentRoleCapability.SUBAGENT_ORCHESTRATION
                ),
                true,
                true
        );
    }
}
