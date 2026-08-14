package org.tavall.ai.execution.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** One provider-neutral model task within a durable Tavall AI execution. */
public record TavallAIModelJob(String jobId, String task, int delegationDepth, Map<String, String> attributes) {
    public TavallAIModelJob {
        jobId = requireText(jobId, "jobId");
        task = requireText(task, "task");
        if (delegationDepth < 0) throw new IllegalArgumentException("delegationDepth must be >= 0");
        attributes = Map.copyOf(new LinkedHashMap<>(attributes == null ? Map.of() : attributes));
    }

    private static String requireText(String value, String fieldName) {
        if (value != null && !value.isBlank()) return value.trim();
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }
}
