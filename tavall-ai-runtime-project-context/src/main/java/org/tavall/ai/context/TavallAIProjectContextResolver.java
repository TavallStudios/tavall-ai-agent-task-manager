package org.tavall.ai.context;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Resolves a bounded project-context request through an explicitly registered source adapter. */
public final class TavallAIProjectContextResolver {
    private final Map<String, TavallAIProjectContextSource> sources;

    public TavallAIProjectContextResolver(Iterable<? extends TavallAIProjectContextSource> sources) {
        Objects.requireNonNull(sources, "sources");
        Map<String, TavallAIProjectContextSource> byType = new LinkedHashMap<>();
        for (TavallAIProjectContextSource source : sources) {
            TavallAIProjectContextSource safeSource = Objects.requireNonNull(source, "source");
            String sourceType = requireText(safeSource.sourceType(), "sourceType");
            if (byType.putIfAbsent(sourceType, safeSource) != null) {
                throw new IllegalArgumentException("Duplicate Tavall AI project context source: " + sourceType);
            }
        }
        this.sources = Map.copyOf(byType);
    }

    public TavallAIProjectContextBundle resolve(TavallAIProjectContextRequest request) throws Exception {
        TavallAIProjectContextRequest safeRequest = Objects.requireNonNull(request, "request");
        TavallAIProjectContextSource source = sources.get(safeRequest.sourceType());
        if (source == null) {
            throw new IllegalArgumentException("No Tavall AI project context source registered for: " + safeRequest.sourceType());
        }
        return Objects.requireNonNull(source.resolve(safeRequest), "project context source result");
    }

    private static String requireText(String value, String fieldName) {
        if (value != null && !value.isBlank()) return value.trim();
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }
}
