package org.tavall.ai.runtime.cloud;

import java.util.Objects;

/** Terminal result returned by one Cloud-hosted Tavall AI execution backend. */
public record TavallAICloudExecutionResult(boolean successful, String resultJson, String errorMessage) {
    public TavallAICloudExecutionResult {
        resultJson = Objects.requireNonNullElse(resultJson, "");
        errorMessage = Objects.requireNonNullElse(errorMessage, "");
        if (successful && !errorMessage.isBlank()) {
            throw new IllegalArgumentException("Successful Tavall AI execution must not contain an error message");
        }
        if (!successful && errorMessage.isBlank()) {
            throw new IllegalArgumentException("Failed Tavall AI execution requires an error message");
        }
    }

    public static TavallAICloudExecutionResult completed(String resultJson) {
        return new TavallAICloudExecutionResult(true, resultJson, "");
    }

    public static TavallAICloudExecutionResult failed(String errorMessage) {
        return new TavallAICloudExecutionResult(false, "", errorMessage);
    }
}
