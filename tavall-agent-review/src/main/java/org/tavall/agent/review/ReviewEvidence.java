package org.tavall.agent.review;

import java.util.Objects;

public record ReviewEvidence(String kind, String detail, boolean passed) {
    public ReviewEvidence {
        kind = Objects.requireNonNull(kind, "kind").trim();
        detail = Objects.requireNonNull(detail, "detail").trim();
        if (kind.isBlank()) throw new IllegalArgumentException("evidence kind cannot be blank");
    }
}
