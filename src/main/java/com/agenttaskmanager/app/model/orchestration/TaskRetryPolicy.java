package com.agenttaskmanager.app.model.orchestration;

public record TaskRetryPolicy(int maxAttempts, int backoffSeconds) {
}
