package org.tavall.ai.execution.distributed;

import java.util.Objects;

/** Ordered evidence for one distributed AI execution attempt. */
public record TavallAIExecutionAttempt(
        String targetId,
        String providerId,
        TavallAIExecutionSurface surface,
        boolean success,
        boolean retryable,
        String resultReference,
        String message
) {
    public TavallAIExecutionAttempt {
        targetId = requireText(targetId, "targetId");
        providerId = requireText(providerId, "providerId");
        surface = Objects.requireNonNull(surface, "surface");
        resultReference = resultReference == null ? "" : resultReference.trim();
        message = message == null ? "" : message.trim();
    }

    static TavallAIExecutionAttempt from(
            TavallAIExecutionTarget target,
            TavallAIExecutionProviderResult result
    ) {
        return new TavallAIExecutionAttempt(
                target.id(),
                target.providerId(),
                target.surface(),
                result.success(),
                result.retryable(),
                result.resultReference(),
                result.message()
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
