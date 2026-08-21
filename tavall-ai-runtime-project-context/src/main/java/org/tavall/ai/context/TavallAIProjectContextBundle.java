package org.tavall.ai.context;

import java.util.List;

/** Immutable context bundle attached to one provider-neutral model execution. */
public record TavallAIProjectContextBundle(
        String sourceType,
        String projectId,
        String sourceVersion,
        List<TavallAIContextItem> items
) {
    public TavallAIProjectContextBundle {
        sourceType = requireText(sourceType, "sourceType");
        projectId = requireText(projectId, "projectId");
        sourceVersion = sourceVersion == null ? "" : sourceVersion.strip();
        items = List.copyOf(items == null ? List.of() : items);
    }

    public static TavallAIProjectContextBundle empty() {
        return new TavallAIProjectContextBundle("none", "none", "", List.of());
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    private static String requireText(String value, String fieldName) {
        if (value != null && !value.isBlank()) return value.trim();
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }
}
