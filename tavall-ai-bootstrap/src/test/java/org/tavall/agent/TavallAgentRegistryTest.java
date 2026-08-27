package org.tavall.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavallAgentRegistryTest {
    @Test
    void registersIndependentAgentsAndCombinesFunctionRequests() {
        TavallAgent agent = agent("implementation");
        TavallAgentRegistry registry = new TavallAgentRegistry(List.of(() -> agent));

        assertEquals(1, registry.size());
        assertEquals(agent, registry.require("implementation"));
        assertEquals(Set.of("repo_read", "repo_write"), agent.requestedFunctionNames());
    }

    @Test
    void rejectsDuplicateAgentIds() {
        TavallAgent first = agent("review");
        TavallAgent second = agent("review");

        assertThrows(
                IllegalArgumentException.class,
                () -> new TavallAgentRegistry(List.of(() -> first, () -> second))
        );
    }

    @Test
    void requiresDeclaredCapabilitiesForAgentSpawningModes() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TavallAgent(
                        "orchestration",
                        "Coordinates subagents.",
                        TavallAgentKind.CONTROL,
                        "Coordinate the session.",
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        true,
                        false
                )
        );

        assertTrue(exception.getMessage().contains("SUBAGENT_ORCHESTRATION"));
    }

    private TavallAgent agent(String id) {
        return new TavallAgent(
                id,
                "Test agent.",
                TavallAgentKind.WORK,
                "Do the test work.",
                Set.of("repo_read"),
                Set.of("repo_write"),
                Set.of(TavallAgentCapability.REPOSITORY_READ),
                false,
                false
        );
    }
}
