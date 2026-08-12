package org.tavall.ai.agent.recovery;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PeerRecoveryPlannerTest {
    private final PeerRecoveryPlanner planner = new PeerRecoveryPlanner();

    @Test
    void usesGuardianWhenNodeAgentIsLost() {
        PeerRecoveryPlan plan = planner.plan(observation(
                TavallAIEnvironment.STAGING,
                PeerHealthState.UNREACHABLE,
                2,
                false,
                true,
                true
        ));

        assertEquals(PeerRecoveryAction.REQUEST_NODE_AGENT_RESTART, plan.action());
        assertFalse(plan.requiresExplicitProductionAuthorization());
    }

    @Test
    void productionMutationRequestsSeparateAuthorization() {
        PeerRecoveryPlan plan = planner.plan(observation(
                TavallAIEnvironment.PRODUCTION,
                PeerHealthState.CRITICAL,
                2,
                false,
                true,
                true
        ));

        assertEquals(PeerRecoveryAction.REQUEST_NODE_AGENT_RESTART, plan.action());
        assertTrue(plan.requiresExplicitProductionAuthorization());
    }

    @Test
    void escalatesOnlyAfterRepeatedTotalLoss() {
        PeerRecoveryPlan first = planner.plan(observation(
                TavallAIEnvironment.STAGING,
                PeerHealthState.UNREACHABLE,
                1,
                false,
                false,
                true
        ));
        PeerRecoveryPlan repeated = planner.plan(observation(
                TavallAIEnvironment.STAGING,
                PeerHealthState.UNREACHABLE,
                3,
                false,
                false,
                true
        ));

        assertEquals(PeerRecoveryAction.REQUEST_DRAIN, first.action());
        assertEquals(PeerRecoveryAction.ESCALATE_PROVIDER_RECOVERY, repeated.action());
    }

    @Test
    void checksColocatedDevAndStagingPeersAggressivelyButNeverHostsAiInProduction() {
        PeerCheckPolicy policy = new PeerCheckPolicy();

        assertEquals(5, policy.interval(
                TavallAIEnvironment.DEVELOPMENT,
                TavallAIEnvironment.STAGING,
                true
        ).toSeconds());
        assertEquals(30, policy.interval(
                TavallAIEnvironment.DEVELOPMENT,
                TavallAIEnvironment.PRODUCTION,
                false
        ).toSeconds());
        assertThrows(IllegalArgumentException.class, () -> policy.interval(
                TavallAIEnvironment.PRODUCTION,
                TavallAIEnvironment.PRODUCTION,
                false
        ));
    }

    private PeerHealthObservation observation(
            TavallAIEnvironment environment,
            PeerHealthState state,
            int failures,
            boolean nodeAgentReachable,
            boolean guardianReachable,
            boolean storageHealthy
    ) {
        return new PeerHealthObservation(
                "node-1",
                "dev-host-1",
                environment,
                state,
                Instant.parse("2026-08-12T00:00:00Z"),
                failures,
                nodeAgentReachable,
                guardianReachable,
                storageHealthy
        );
    }
}
