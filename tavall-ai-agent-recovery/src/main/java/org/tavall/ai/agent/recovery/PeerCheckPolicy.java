package org.tavall.ai.agent.recovery;

import java.time.Duration;
import java.util.Objects;

/**
 * Deterministic polling policy for AI peer supervision.
 *
 * <p>Development and staging may be checked aggressively, especially when logical nodes share a
 * physical development host. Production targets are checked less aggressively and only from an
 * off-production execution environment. This class chooses cadence, not authority.</p>
 */
public final class PeerCheckPolicy {
    private static final Duration COLOCATED_NON_PRODUCTION = Duration.ofSeconds(5);
    private static final Duration NON_PRODUCTION = Duration.ofSeconds(15);
    private static final Duration PRODUCTION = Duration.ofSeconds(30);

    public Duration interval(
            TavallAIEnvironment observerEnvironment,
            TavallAIEnvironment targetEnvironment,
            boolean samePhysicalHost
    ) {
        Objects.requireNonNull(observerEnvironment, "observerEnvironment");
        Objects.requireNonNull(targetEnvironment, "targetEnvironment");

        if (observerEnvironment == TavallAIEnvironment.PRODUCTION) {
            throw new IllegalArgumentException("Custom Tavall AI recovery execution is not hosted in PRODUCTION");
        }
        if (targetEnvironment == TavallAIEnvironment.PRODUCTION) {
            return PRODUCTION;
        }
        return samePhysicalHost ? COLOCATED_NON_PRODUCTION : NON_PRODUCTION;
    }
}
