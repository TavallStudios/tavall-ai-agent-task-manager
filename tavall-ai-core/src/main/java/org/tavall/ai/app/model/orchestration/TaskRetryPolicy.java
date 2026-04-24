package org.tavall.ai.app.model.orchestration;

public record TaskRetryPolicy(int maxAttempts, int backoffSeconds) {
}

