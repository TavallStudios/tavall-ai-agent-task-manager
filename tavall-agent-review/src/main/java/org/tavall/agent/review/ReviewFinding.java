package org.tavall.agent.review;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record ReviewFinding(
        ReviewSeverity severity,
        double confidence,
        ReviewCategory category,
        String file,
        int line,
        String symbol,
        String title,
        String explanation,
        List<String> evidence,
        String suggestedDirection,
        String fingerprint
) {
    public ReviewFinding {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(category, "category");
        file = Objects.requireNonNull(file, "file").trim();
        symbol = symbol == null ? "" : symbol.trim();
        title = Objects.requireNonNull(title, "title").trim();
        explanation = Objects.requireNonNull(explanation, "explanation").trim();
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        suggestedDirection = suggestedDirection == null ? "" : suggestedDirection.trim();
        if (confidence < 0.0 || confidence > 1.0) throw new IllegalArgumentException("confidence must be between 0 and 1");
        if (line < 0) throw new IllegalArgumentException("line cannot be negative");
    }

    public static ReviewFinding create(
            ReviewSeverity severity,
            double confidence,
            ReviewCategory category,
            String file,
            int line,
            String symbol,
            String title,
            String explanation,
            List<String> evidence,
            String suggestedDirection
    ) {
        String fingerprint = fingerprint(category, file, symbol, title);
        return new ReviewFinding(severity, confidence, category, file, line, symbol, title,
                explanation, evidence, suggestedDirection, fingerprint);
    }

    private static String fingerprint(ReviewCategory category, String file, String symbol, String title) {
        String canonical = category.name() + "\n" + normalize(file) + "\n" + normalize(symbol) + "\n" + normalize(title);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
