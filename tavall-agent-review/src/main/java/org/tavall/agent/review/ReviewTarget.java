package org.tavall.agent.review;

import java.util.Objects;

public record ReviewTarget(ReviewTargetType type, String value, String base) {
    public ReviewTarget {
        Objects.requireNonNull(type, "type");
        if (value == null || value.isBlank()) throw new IllegalArgumentException("target value cannot be blank");
        base = base == null ? "" : base;
    }

    public static ReviewTarget pullRequest(int number) {
        if (number <= 0) throw new IllegalArgumentException("pull request number must be positive");
        return new ReviewTarget(ReviewTargetType.PULL_REQUEST, Integer.toString(number), "");
    }
}
