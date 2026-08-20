package org.tavall.ai.app.mcp.tools.vectormemory;

public record CodeImpactToolRequest(
    String repository,
    Integer pullRequestNumber,
    String repoPath
) {
}
