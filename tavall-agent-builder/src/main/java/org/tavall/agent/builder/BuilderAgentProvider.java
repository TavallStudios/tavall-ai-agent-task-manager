package org.tavall.agent.builder;

import org.tavall.agent.TavallAgent;
import org.tavall.agent.TavallAgentCapability;
import org.tavall.agent.TavallAgentInstructions;
import org.tavall.agent.TavallAgentKind;
import org.tavall.agent.TavallAgentProvider;
import org.tavall.dependency.annotations.DelegatesTo;

import java.util.Set;

/** Builder domain agent. The model/runtime is supplied by the parent Tavall AI runtime. */
@DelegatesTo
public final class BuilderAgentProvider implements TavallAgentProvider {
    public static final String AGENT_ID = "builder";

    @Override
    public TavallAgent agent() {
        return new TavallAgent(
                AGENT_ID,
                "Composes Builder planning, simulation, repair, and visual critique around existing Builder artifacts.",
                TavallAgentKind.WORK,
                TavallAgentInstructions.load(BuilderAgentProvider.class, "ROLE.md"),
                Set.of(),
                Set.of(),
                Set.of("distributed-execution"),
                Set.of(
                        TavallAgentCapability.REPOSITORY_READ,
                        TavallAgentCapability.RUNTIME_E2E
                ),
                false,
                false
        );
    }
}
