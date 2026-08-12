package org.tavall.ai.agent.recovery;

import java.util.Objects;

/** One bounded recovery proposal. Execution still requires the target-side Cloud authority. */
public record PeerRecoveryPlan(
        String nodeId,
        PeerRecoveryAction action,
        String reason,
        boolean requiresExplicitProductionAuthorization
) {
    public PeerRecoveryPlan {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        nodeId = nodeId.trim();
        action = Objects.requireNonNull(action, "action");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        reason = reason.trim();
    }
}
