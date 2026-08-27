package org.tavall.ai.bootstrap;

import java.util.LinkedHashSet;
import java.util.Set;

/** Loadable Tavall AI behavior/domain module descriptor. It is not a process runtime. */
public record TavallAIModule(
        String id,
        String description,
        Set<String> requiredModuleIds
) {
    public TavallAIModule {
        id = requireText(id, "id");
        description = requireText(description, "description");
        LinkedHashSet<String> dependencies = new LinkedHashSet<>();
        if (requiredModuleIds != null) {
            for (String requiredModuleId : requiredModuleIds) {
                dependencies.add(requireText(requiredModuleId, "requiredModuleId"));
            }
        }
        if (dependencies.contains(id)) {
            throw new IllegalArgumentException("A Tavall AI module cannot require itself: " + id);
        }
        requiredModuleIds = Set.copyOf(dependencies);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
