package org.tavall.agent.web;

import org.junit.jupiter.api.Test;
import org.tavall.agent.TavallAgent;
import org.tavall.agent.TavallAgentCapability;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavallWebAgentTest {
    @Test
    void webAgentUsesTheSharedRuntimeAndCanWorkOnRealFrontendRepositories() {
        TavallAgent agent = new WebAgentProvider().agent();

        assertEquals("web", agent.id());
        assertEquals(Set.of("distributed-execution"), agent.requiredRuntimeModuleIds());
        assertTrue(agent.requiredFunctionNames().containsAll(Set.of("repo_read", "repo_search")));
        assertTrue(agent.optionalFunctionNames().containsAll(Set.of(
                "product_intelligence_read",
                "product_intelligence_record"
        )));
        assertTrue(agent.capabilities().containsAll(Set.of(
                TavallAgentCapability.FUNCTION_DISCOVERY,
                TavallAgentCapability.REPOSITORY_READ,
                TavallAgentCapability.REPOSITORY_WRITE,
                TavallAgentCapability.GIT_CHECKPOINT,
                TavallAgentCapability.RUNTIME_E2E
        )));
        assertFalse(agent.maySpawnSubagents());
        assertFalse(agent.mayRequestDistributedSession());
    }

    @Test
    void firstSliceDoesNotInventInternalWebRoles() {
        String instructions = new WebAgentProvider().agent().instructions();

        assertFalse(instructions.contains("Web Director"));
        assertFalse(instructions.contains("UX Architect"));
        assertFalse(instructions.contains("Visual Designer"));
        assertFalse(instructions.contains("Frontend Specialist"));
        assertFalse(instructions.contains("Visual Critic"));
        assertFalse(instructions.contains("Web QA"));
    }


    @Test
    void webAgentRequiresDeterministicInspectableVisualEvidence() {
        String instructions = new WebAgentProvider().agent().instructions();

        assertTrue(instructions.contains("desktop and mobile"));
        assertTrue(instructions.contains("commit and push"));
        assertTrue(instructions.contains("direct committed links"));
        assertTrue(instructions.contains("never substitute prose scoring"));
        assertTrue(instructions.contains("base64"));
        assertTrue(instructions.contains("explicit synthesis"));
    }

    @Test
    void webAgentContractContainsNoAiNamedTypes() {
        assertTrue(List.of(
                WebAgentProvider.class,
                WebDesignCandidate.class,
                WebDesignComparison.class,
                WebDesignDecision.class,
                WebDesignIntelligenceCategory.class,
                WebDesignIntelligenceService.class
        ).stream().noneMatch(type -> type.getSimpleName().contains("AI")));
    }
}
