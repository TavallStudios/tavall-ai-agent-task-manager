package org.tavall.ai.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One normalized context item with source provenance retained as metadata. */
public record TavallAIContextItem(
        String id,
        TavallAIContextKind kind,
        String title,
        String content,
        Map<String, String> metadata
) {
    public TavallAIContextItem {
        id = requireText(id, "id");
        kind = Objects.requireNonNull(kind, "kind");
        title = title == null ? "" : title.strip();
        content = Objects.requireNonNullElse(content, "");
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata == null ? Map.of() : metadata));
    }

    public TavallAIContextItem withContent(String replacementContent) {
        return new TavallAIContextItem(id, kind, title, replacementContent, metadata);
    }

    private static String requireText(String value, String fieldName) {
        if (value != null && !value.isBlank()) return value.trim();
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }
}
