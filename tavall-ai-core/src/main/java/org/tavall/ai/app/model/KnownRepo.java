package org.tavall.ai.app.model;

public record KnownRepo(
    String displayName,
    String projectKey,
    String repoPath,
    String locationLabel
) {
}

