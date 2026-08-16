package org.tavall.agent.review;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ReviewContext(
        ReviewRequest request,
        String canonicalText,
        Set<String> inspectedAreas,
        List<ReviewEvidence> evidence
) {
    public ReviewContext {
        Objects.requireNonNull(request, "request");
        canonicalText = Objects.requireNonNull(canonicalText, "canonicalText");
        inspectedAreas = Set.copyOf(inspectedAreas == null ? Set.of() : inspectedAreas);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
    }
}
