package org.tavall.agent.review;

import java.util.Objects;
import java.util.Set;

public record ReviewRequest(
        String repository,
        ReviewTarget target,
        String exactHeadSha,
        ReviewProfile profile,
        Set<ReviewCategory> focuses,
        ReviewPublicationPolicy publicationPolicy
) {
    public ReviewRequest {
        repository = Objects.requireNonNull(repository, "repository").trim();
        Objects.requireNonNull(target, "target");
        exactHeadSha = Objects.requireNonNull(exactHeadSha, "exactHeadSha").trim();
        Objects.requireNonNull(profile, "profile");
        focuses = Set.copyOf(focuses == null ? Set.of() : focuses);
        publicationPolicy = publicationPolicy == null ? ReviewPublicationPolicy.localOnly() : publicationPolicy;
        if (repository.isBlank()) throw new IllegalArgumentException("repository cannot be blank");
        if (exactHeadSha.isBlank()) throw new IllegalArgumentException("exact head SHA cannot be blank");
    }

    public static ReviewRequest pullRequest(String repository, int pullRequestNumber, String exactHeadSha, ReviewProfile profile) {
        return new ReviewRequest(repository, ReviewTarget.pullRequest(pullRequestNumber), exactHeadSha, profile,
                Set.of(), ReviewPublicationPolicy.localOnly());
    }

    public String cacheFingerprint() {
        return repository + ":" + target.type() + ":" + target.value() + ":" + exactHeadSha;
    }
}
