package org.tavall.agent.review;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReviewRuntimeTest {

    @Test
    void pullRequestReviewPinsExactHeadAndDoesNotPublishByDefault() {
        ReviewRequest request = ReviewRequest.pullRequest(
                "TavallStudios/example",
                42,
                "0123456789abcdef",
                ReviewProfile.STANDARD
        );

        assertEquals("TavallStudios/example", request.repository());
        assertEquals(ReviewTargetType.PULL_REQUEST, request.target().type());
        assertEquals("42", request.target().value());
        assertEquals("0123456789abcdef", request.exactHeadSha());
        assertEquals(ReviewProfile.STANDARD, request.profile());
        assertFalse(request.publicationPolicy().publish());
    }

    @Test
    void findingFingerprintSurvivesLineMovementButChangesForDifferentSemantics() {
        ReviewFinding first = ReviewFinding.create(
                ReviewSeverity.BLOCKING,
                0.98,
                ReviewCategory.CONCURRENCY,
                "src/main/java/example/Worker.java",
                40,
                "run",
                "Lost update in worker state",
                "Two writers race on the same state.",
                List.of("concurrency-analyzer:write-path"),
                "Use the existing atomic state transition."
        );
        ReviewFinding moved = ReviewFinding.create(
                ReviewSeverity.BLOCKING,
                0.98,
                ReviewCategory.CONCURRENCY,
                "src/main/java/example/Worker.java",
                91,
                "run",
                "Lost update in worker state",
                "Two writers race on the same state.",
                List.of("concurrency-analyzer:write-path"),
                "Use the existing atomic state transition."
        );
        ReviewFinding different = ReviewFinding.create(
                ReviewSeverity.BLOCKING,
                0.98,
                ReviewCategory.SECURITY,
                "src/main/java/example/Worker.java",
                91,
                "run",
                "Untrusted command execution",
                "Repository input reaches a shell.",
                List.of("security-analyzer:command-path"),
                "Use the sandbox command allowlist."
        );

        assertEquals(first.fingerprint(), moved.fingerprint());
        assertNotEquals(first.fingerprint(), different.fingerprint());
    }

    @Test
    void reportRequestsChangesForBlockingFindingAndTracksExactHead() {
        ReviewFinding finding = ReviewFinding.create(
                ReviewSeverity.BLOCKING,
                0.95,
                ReviewCategory.CORRECTNESS,
                "src/main/java/example/Service.java",
                12,
                "save",
                "Write result is discarded",
                "The failed write is reported as successful.",
                List.of("unit-test:failed-write"),
                "Propagate the failed result."
        );

        ReviewReport report = ReviewReport.create(
                "TavallStudios/example",
                "fedcba9876543210",
                ReviewProfile.DEEP,
                List.of(finding),
                List.of(new ReviewEvidence("test", "./gradlew test", true)),
                Set.of("src/main/java/example/Service.java")
        );

        assertEquals("fedcba9876543210", report.exactHeadSha());
        assertEquals(ReviewDisposition.REQUEST_CHANGES, report.disposition());
        assertTrue(report.findings().contains(finding));
    }
}
