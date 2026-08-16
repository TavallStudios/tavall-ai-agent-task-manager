package org.tavall.agent.review;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ReviewReport(
        String repository,
        String exactHeadSha,
        ReviewProfile profile,
        ReviewDisposition disposition,
        List<ReviewFinding> findings,
        List<ReviewEvidence> validationEvidence,
        Set<String> inspectedAreas,
        Instant completedAt
) {
    public ReviewReport {
        repository = Objects.requireNonNull(repository, "repository");
        exactHeadSha = Objects.requireNonNull(exactHeadSha, "exactHeadSha");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(disposition, "disposition");
        findings = List.copyOf(findings == null ? List.of() : findings);
        validationEvidence = List.copyOf(validationEvidence == null ? List.of() : validationEvidence);
        inspectedAreas = Set.copyOf(inspectedAreas == null ? Set.of() : inspectedAreas);
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
    }

    public static ReviewReport create(
            String repository,
            String exactHeadSha,
            ReviewProfile profile,
            List<ReviewFinding> findings,
            List<ReviewEvidence> validationEvidence,
            Set<String> inspectedAreas
    ) {
        List<ReviewFinding> ordered = (findings == null ? List.<ReviewFinding>of() : findings).stream()
                .sorted(Comparator.comparingInt((ReviewFinding finding) -> severityRank(finding.severity()))
                        .thenComparing(ReviewFinding::file)
                        .thenComparingInt(ReviewFinding::line))
                .toList();
        ReviewDisposition disposition = ordered.stream().anyMatch(f -> f.severity() == ReviewSeverity.BLOCKING)
                ? ReviewDisposition.REQUEST_CHANGES
                : ordered.stream().anyMatch(f -> f.severity() == ReviewSeverity.IMPORTANT)
                ? ReviewDisposition.COMMENT
                : ReviewDisposition.APPROVE;
        return new ReviewReport(repository, exactHeadSha, profile, disposition, ordered,
                validationEvidence, inspectedAreas, Instant.now());
    }

    private static int severityRank(ReviewSeverity severity) {
        return switch (severity) {
            case BLOCKING -> 0;
            case IMPORTANT -> 1;
            case SUGGESTION -> 2;
        };
    }
}
