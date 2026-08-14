package org.tavall.ai.execution.model;

import java.time.Duration;
import java.util.Objects;

/** Runtime-level model budget. Infrastructure CPU/RAM/concurrency limits remain host/Cloud policy. */
public record TavallAIModelExecutionBudget(Duration timeout, int maxToolCalls, int maxDelegations) {
    public TavallAIModelExecutionBudget {
        timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        if (maxToolCalls < 0) throw new IllegalArgumentException("maxToolCalls must be >= 0");
        if (maxDelegations < 0) throw new IllegalArgumentException("maxDelegations must be >= 0");
    }
}
