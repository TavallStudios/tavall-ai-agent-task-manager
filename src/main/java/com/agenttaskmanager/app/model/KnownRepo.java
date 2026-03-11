package com.agenttaskmanager.app.model;

public record KnownRepo(
    String displayName,
    String projectKey,
    String repoPath,
    String locationLabel
) {
}
