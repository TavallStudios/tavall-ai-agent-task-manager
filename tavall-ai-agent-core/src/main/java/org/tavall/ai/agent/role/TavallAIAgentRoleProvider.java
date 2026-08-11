package org.tavall.ai.agent.role;

/** ServiceLoader boundary implemented by independently deployable Tavall AI role modules. */
public interface TavallAIAgentRoleProvider {
    TavallAIAgentRole role();
}
