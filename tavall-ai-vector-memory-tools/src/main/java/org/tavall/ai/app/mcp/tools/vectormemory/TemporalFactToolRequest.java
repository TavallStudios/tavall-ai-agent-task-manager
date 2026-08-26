package org.tavall.ai.app.mcp.tools.vectormemory;

public record TemporalFactToolRequest(
    String sourceNode,
    String edgeName,
    String fact,
    String targetNode
) {
}
