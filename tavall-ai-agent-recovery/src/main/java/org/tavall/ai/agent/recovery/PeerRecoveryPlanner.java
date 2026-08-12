package org.tavall.ai.agent.recovery;

import java.util.Objects;

/**
 * Deterministic first-pass recovery planner. The model may reason over the same evidence, but it
 * may only request an action represented here and exposed by its restricted Function Catalog view.
 */
public final class PeerRecoveryPlanner {
    public PeerRecoveryPlan plan(PeerHealthObservation observation) {
        Objects.requireNonNull(observation, "observation");

        PeerRecoveryAction action = switch (observation.state()) {
            case HEALTHY -> PeerRecoveryAction.NONE;
            case RECOVERING -> PeerRecoveryAction.INSPECT_EXTENDED_HEALTH;
            case DEGRADED -> degradedAction(observation);
            case CRITICAL -> criticalAction(observation);
            case UNREACHABLE -> unreachableAction(observation);
        };

        return new PeerRecoveryPlan(
                observation.nodeId(),
                action,
                reason(action, observation),
                observation.environment() == TavallAIEnvironment.PRODUCTION
                        && action != PeerRecoveryAction.NONE
        );
    }

    private PeerRecoveryAction degradedAction(PeerHealthObservation observation) {
        if (!observation.storageHealthy() && observation.consecutiveFailures() >= 2) {
            return PeerRecoveryAction.REQUEST_STORAGE_REPAIR;
        }
        return PeerRecoveryAction.INSPECT_EXTENDED_HEALTH;
    }

    private PeerRecoveryAction criticalAction(PeerHealthObservation observation) {
        if (!observation.nodeAgentReachable() && observation.recoveryGuardianReachable()) {
            return PeerRecoveryAction.REQUEST_NODE_AGENT_RESTART;
        }
        if (!observation.storageHealthy()) {
            return PeerRecoveryAction.REQUEST_STORAGE_REPAIR;
        }
        return PeerRecoveryAction.REQUEST_ALLOWLISTED_SERVICE_RECOVERY;
    }

    private PeerRecoveryAction unreachableAction(PeerHealthObservation observation) {
        if (observation.recoveryGuardianReachable()) {
            return PeerRecoveryAction.REQUEST_NODE_AGENT_RESTART;
        }
        return observation.consecutiveFailures() >= 3
                ? PeerRecoveryAction.ESCALATE_PROVIDER_RECOVERY
                : PeerRecoveryAction.REQUEST_DRAIN;
    }

    private String reason(PeerRecoveryAction action, PeerHealthObservation observation) {
        return switch (action) {
            case NONE -> "Peer reports healthy bounded evidence.";
            case INSPECT_EXTENDED_HEALTH -> "Peer requires more bounded evidence before mutation.";
            case REQUEST_NODE_AGENT_RESTART -> "Node Agent is unavailable while the independent recovery guardian is reachable.";
            case REQUEST_ALLOWLISTED_SERVICE_RECOVERY -> "Peer is critical but node-level control remains reachable.";
            case REQUEST_STORAGE_REPAIR -> "Storage health is failing and requires a typed repair attempt.";
            case REQUEST_DRAIN -> "Peer is unreachable; remove it from active routing while recovery evidence is gathered.";
            case REQUEST_SIGNED_RELEASE_ROLLBACK -> "A previously verified signed release rollback was selected.";
            case ESCALATE_PROVIDER_RECOVERY -> "Node Agent and recovery guardian remain unreachable across repeated observations.";
        } + " failures=" + observation.consecutiveFailures();
    }
}
