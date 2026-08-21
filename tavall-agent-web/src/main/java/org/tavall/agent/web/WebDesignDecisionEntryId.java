package org.tavall.agent.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Stable path-safe identity for one candidate inside one Web design comparison decision. */
public record WebDesignDecisionEntryId(String value) {
    private static final String PREFIX = "design-decision-";

    public WebDesignDecisionEntryId {
        value = Objects.requireNonNull(value, "value");
        if (!value.matches(PREFIX + "[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Web design decision entry id has an invalid format");
        }
    }

    public static WebDesignDecisionEntryId from(String comparisonId, String candidateId) {
        String safeComparisonId = WebDesignCandidate.requireIdentifier(comparisonId, "comparisonId");
        String safeCandidateId = WebDesignCandidate.requireIdentifier(candidateId, "candidateId");
        String canonical = safeComparisonId.length() + ":" + safeComparisonId
                + safeCandidateId.length() + ":" + safeCandidateId;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return new WebDesignDecisionEntryId(PREFIX + HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
