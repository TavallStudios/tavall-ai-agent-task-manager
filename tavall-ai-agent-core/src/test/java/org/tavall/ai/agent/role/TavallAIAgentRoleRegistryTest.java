package org.tavall.ai.agent.role;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavallAIAgentRoleRegistryTest {
    @Test
    void registersIndependentRoleProvidersAndCombinesFunctionRequests() {
        TavallAIAgentRole role = role("implementation");
        TavallAIAgentRoleRegistry registry = new TavallAIAgentRoleRegistry(List.of(() -> role));

        assertEquals(1, registry.size());
        assertEquals(role, registry.require("implementation"));
        assertEquals(Set.of("repo_read", "repo_write"), role.requestedFunctionNames());
    }

    @Test
    void rejectsDuplicateRoleIds() {
        TavallAIAgentRole first = role("review");
        TavallAIAgentRole second = role("review");

        assertThrows(
                IllegalArgumentException.class,
                () -> new TavallAIAgentRoleRegistry(List.of(() -> first, () -> second))
        );
    }

    @Test
    void requiresDeclaredCapabilitiesForAgentSpawningModes() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TavallAIAgentRole(
                        "orchestration",
                        "Coordinates subagents.",
                        TavallAIAgentRoleKind.CONTROL,
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

    private TavallAIAgentRole role(String id) {
        return new TavallAIAgentRole(
                id,
                "Test role.",
                TavallAIAgentRoleKind.WORK,
                "Do the test work.",
                Set.of("repo_read"),
                Set.of("repo_write"),
                Set.of(TavallAIAgentRoleCapability.REPOSITORY_READ),
                false,
                false
        );
    }
}
