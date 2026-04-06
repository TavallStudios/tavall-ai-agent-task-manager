package org.tavall.ai.app.model;

public record OperatorToolCard(
    String toolId,
    String title,
    String status,
    String summary,
    String description,
    String href,
    String launchLabel,
    String command
) {
}

