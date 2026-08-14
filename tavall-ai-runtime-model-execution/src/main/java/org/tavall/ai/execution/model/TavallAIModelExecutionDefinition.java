package org.tavall.ai.execution.model;

import org.tavall.agent.TavallAgent;

import java.util.Objects;

/** Binds the canonical non-AI Tavall agent descriptor to one actual model provider. */
public record TavallAIModelExecutionDefinition(TavallAgent agent, String providerId) {
    public TavallAIModelExecutionDefinition {
        agent = Objects.requireNonNull(agent, "agent");
        if (providerId == null || providerId.isBlank()) throw new IllegalArgumentException("providerId must not be blank");
        providerId = providerId.trim();
    }
}
