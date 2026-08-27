package org.tavall.ai.execution.distributed;

/** Result returned by one already-authorized execution target provider attempt. */
public record TavallAIExecutionProviderResult(
        boolean success,
        boolean retryable,
        String resultReference,
        String message
) {
    public TavallAIExecutionProviderResult {
        resultReference = resultReference == null ? "" : resultReference.trim();
        message = message == null ? "" : message.trim();
        if (success && resultReference.isBlank()) {
            throw new IllegalArgumentException("Successful execution must return a result reference");
        }
        if (success && retryable) {
            throw new IllegalArgumentException("Successful execution cannot be retryable");
        }
    }

    public static TavallAIExecutionProviderResult success(String resultReference) {
        return new TavallAIExecutionProviderResult(true, false, resultReference, "");
    }

    public static TavallAIExecutionProviderResult retryableFailure(String message) {
        return new TavallAIExecutionProviderResult(false, true, "", message);
    }

    public static TavallAIExecutionProviderResult terminalFailure(String message) {
        return new TavallAIExecutionProviderResult(false, false, "", message);
    }
}
