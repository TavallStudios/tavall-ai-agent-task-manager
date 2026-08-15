package org.tavall.agent.orchestration;

import org.tavall.agent.TavallAgent;
import org.tavall.agent.TavallAgentCapability;
import org.tavall.agent.TavallAgentInstructions;
import org.tavall.agent.TavallAgentKind;
import org.tavall.agent.TavallAgentProvider;
import org.tavall.dependency.annotations.DelegatesTo;

import java.util.Set;

/** Session-level control agent that composes specialized Tavall agents inside one model session. */
@DelegatesTo
public final class OrchestrationAgentProvider implements TavallAgentProvider {
    public static final String AGENT_ID = "orchestration";

    @Override
    public TavallAgent agent() {
        return new TavallAgent(
                AGENT_ID,
                "Coordinates specialized Tavall agents and subagents inside one owning model session.",
                TavallAgentKind.CONTROL,
                TavallAgentInstructions.load(OrchestrationAgentProvider.class, "ROLE.md"),
                Set.of("ai_spawn_subagent", "ai_inspect_subagent", "ai_join_subagent"),
                Set.of("ai_request_distributed_job", "ai_inspect_job", "ci_verify", "github_inspect_pr"),
                Set.of(
                        TavallAgentCapability.FUNCTION_DISCOVERY,
                        TavallAgentCapability.REPOSITORY_READ,
                        TavallAgentCapability.PULL_REQUEST_READ,
                        TavallAgentCapability.LOCAL_CI,
                        TavallAgentCapability.DISTRIBUTED_SCHEDULING,
                        TavallAgentCapability.SUBAGENT_ORCHESTRATION
                ),
                true,
                true
        );
    }
}
