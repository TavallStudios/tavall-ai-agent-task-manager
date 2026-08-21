package org.tavall.ai.context;

import java.util.Set;

/** Bounded request for relevant context from one project-context source. */
public record TavallAIProjectContextRequest(
        String sourceType,
        String projectId,
        String query,
        Set<TavallAIContextKind> kinds,
        int maxItems,
        int maxCharacters
) {
    public TavallAIProjectContextRequest {
        sourceType = requireText(sourceType, "sourceType");
        projectId = requireText(projectId, "projectId");
        query = query == null ? "" : query.strip();
        kinds = Set.copyOf(kinds == null ? Set.of(TavallAIContextKind.values()) : kinds);
        if (maxItems <= 0) throw new IllegalArgumentException("maxItems must be > 0");
        if (maxCharacters <= 0) throw new IllegalArgumentException("maxCharacters must be > 0");
    }

    private static String requireText(String value, String fieldName) {
        if (value != null && !value.isBlank()) return value.trim();
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }
}