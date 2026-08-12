package org.tavall.ai.agent.recovery;

/**
 * Typed recovery intents emitted by AI planning. The AI runtime does not execute these directly;
 * Tavall Cloud must separately authorize and route any concrete target mutation.
 */
public enum PeerRecoveryAction {
    NONE,
    INSPECT_EXTENDED_HEALTH,
    REQUEST_NODE_AGENT_RESTART,
    REQUEST_ALLOWLISTED_SERVICE_RECOVERY,
    REQUEST_STORAGE_REPAIR,
    REQUEST_DRAIN,
    REQUEST_SIGNED_RELEASE_ROLLBACK,
    ESCALATE_PROVIDER_RECOVERY
}
