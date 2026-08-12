package org.tavall.ai.agent.recovery;

/** Health state derived from bounded Tavall Cloud evidence. */
public enum PeerHealthState {
    HEALTHY,
    DEGRADED,
    CRITICAL,
    UNREACHABLE,
    RECOVERING
}
