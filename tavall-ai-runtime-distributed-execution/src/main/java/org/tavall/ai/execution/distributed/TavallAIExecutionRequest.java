package org.tavall.ai.execution.distributed;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** AI-specific routing request correlated to an authority-owned durable job/lease. */
public record TavallAIExecutionRequest(
        String executionId,
        String jobId,
        long jobVersion,
        Set<String> requiredCapabilities,
        List<TavallAIExecutionSurface> preferredSurfaces,
        Set<TavallAIExecutionSurface> allowedSurfaces,
        int maximumAttempts,
        String task,
        String authorityReference
) {
    private static final int MAXIMUM_ATTEMPT_LIMIT = 16;

    public TavallAIExecutionRequest {
        executionId = requireText(executionId, "executionId");
        jobId = requireText(jobId, "jobId");
        if (jobVersion < 0) {
            throw new IllegalArgumentException("jobVersion must be non-negative");
        }
        requiredCapabilities = copyCapabilities(requiredCapabilities);
        preferredSurfaces = preferredSurfaces == null
                ? List.of()
                : List.copyOf(new ArrayList<>(preferredSurfaces));
        allowedSurfaces = allowedSurfaces == null ? Set.of() : Set.copyOf(allowedSurfaces);
        if (maximumAttempts < 1 || maximumAttempts > MAXIMUM_ATTEMPT_LIMIT) {
            throw new IllegalArgumentException(
                    "maximumAttempts must be between 1 and " + MAXIMUM_ATTEMPT_LIMIT
            );
        }
        task = requireText(task, "task");
        authorityReference = requireText(authorityReference, "authorityReference");
    }

    public boolean allows(TavallAIExecutionSurface surface) {
        return allowedSurfaces.isEmpty() || allowedSurfaces.contains(surface);
    }

    public int surfacePreference(TavallAIExecutionSurface surface) {
        int index = preferredSurfaces.indexOf(surface);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private static Set<String> copyCapabilities(Set<String> values) {
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                copy.add(requireText(value, "required capability"));
            }
        }
        return Set.copyOf(copy);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
