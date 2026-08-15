package org.tavall.agent.web;

import java.time.Instant;
import java.util.Objects;

/** Selection and rationale produced after comparing rendered Web design candidates. */
public record WebDesignDecision(
        String comparisonId,
        String selectedCandidateId,
        String rationale,
        Instant decidedAt
) {
    public WebDesignDecision {
        comparisonId = WebDesignCandidate.requireIdentifier(comparisonId, "comparisonId");
        selectedCandidateId = WebDesignCandidate.requireIdentifier(selectedCandidateId, "selectedCandidateId");
        rationale = WebDesignCandidate.requireText(rationale, "rationale");
        decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
    }
}
