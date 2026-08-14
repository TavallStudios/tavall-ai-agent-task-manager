package org.tavall.ai.execution.distributed;

import java.util.List;
import java.util.Objects;

/** Terminal distributed execution result with ordered provider attempt evidence. */
public record TavallAIExecutionResult(
        TavallAIExecutionStatus status,
        String resultReference,
        String message,
        List<TavallAIExecutionAttempt> attempts
) {
    public TavallAIExecutionResult {
        status = Objects.requireNonNull(status, "status");
        resultReference = resultReference == null ? "" : resultReference.trim();
        message = message == null ? "" : message.trim();
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        if (status == TavallAIExecutionStatus.COMPLETED && resultReference.isBlank()) {
            throw new IllegalArgumentException("Completed execution must include a result reference");
        }
    }

    public boolean success() {
        return status == TavallAIExecutionStatus.COMPLETED;
    }

    static TavallAIExecutionResult completed(
            String resultReference,
            List<TavallAIExecutionAttempt> attempts
    ) {
        return new TavallAIExecutionResult(
                TavallAIExecutionStatus.COMPLETED,
                resultReference,
                "",
                attempts
        );
    }

    static TavallAIExecutionResult failed(
            TavallAIExecutionStatus status,
            String message,
            List<TavallAIExecutionAttempt> attempts
    ) {
        if (status == TavallAIExecutionStatus.COMPLETED) {
            throw new IllegalArgumentException("Failure result cannot use COMPLETED status");
        }
        return new TavallAIExecutionResult(status, "", message, attempts);
    }
}
