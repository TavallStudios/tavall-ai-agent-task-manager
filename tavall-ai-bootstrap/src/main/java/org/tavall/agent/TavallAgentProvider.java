package org.tavall.agent;

/** ServiceLoader boundary implemented by independently packaged Tavall agents. */
public interface TavallAgentProvider {
    TavallAgent agent();
}
