package org.tavall.ai.execution.distributed;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One execution target already authorized for consideration by its target provider.
 *
 * <p>The opaque target reference is interpreted only by the owning provider. The router must not
 * treat it as authority, credentials, or a transport endpoint.</p>
 */
public record TavallAIExecutionTarget(
        String id,
        String providerId,
        TavallAIExecutionSurface surface,
        Set<String> capabilities,
        boolean ready,
        int routingPriority,
        String targetReference
) {
    public TavallAIExecutionTarget {
        id = requireText(id, "id");
        providerId = requireText(providerId, "providerId");
        surface = Objects.requireNonNull(surface, "surface");
        capabilities = copyCapabilities(capabilities);
        targetReference = requireText(targetReference, "targetReference");
    }

    public boolean supportsAll(Set<String> requiredCapabilities) {
        return capabilities.containsAll(requiredCapabilities == null ? Set.of() : requiredCapabilities);
    }

    private static Set<String> copyCapabilities(Set<String> values) {
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                copy.add(requireText(value, "capability"));
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
