package org.tavall.agent.web;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A meaningful A/B/C-or-more comparison owned by the Web Agent. */
public record WebDesignComparison(
        String comparisonId,
        String productId,
        String brief,
        List<WebDesignCandidate> candidates
) {
    public WebDesignComparison {
        comparisonId = WebDesignCandidate.requireIdentifier(comparisonId, "comparisonId");
        productId = WebDesignCandidate.requireText(productId, "productId");
        brief = WebDesignCandidate.requireText(brief, "brief");
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (candidates.size() < 3) {
            throw new IllegalArgumentException("A Web design comparison requires at least three candidates");
        }

        Set<String> ids = new HashSet<>();
        for (WebDesignCandidate candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("candidates must not contain null");
            }
            if (!ids.add(candidate.id())) {
                throw new IllegalArgumentException("Duplicate Web design candidate id: " + candidate.id());
            }
        }
    }

    public WebDesignCandidate requireSelectedCandidate(WebDesignDecision decision) {
        if (!comparisonId.equals(decision.comparisonId())) {
            throw new IllegalArgumentException("Decision belongs to a different comparison");
        }
        return candidates.stream()
                .filter(candidate -> candidate.id().equals(decision.selectedCandidateId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Decision selected unknown candidate: " + decision.selectedCandidateId()
                ));
    }
}
