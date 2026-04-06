package org.tavall.ai.app.harness.tools;

import org.tavall.ai.app.mcp.DownstreamMcpToolResult;
import java.util.List;
import java.util.Map;

public record HarnessToolBundleResult(
    String bundleName,
    Map<String, Object> summary,
    Map<String, Object> sections,
    List<DownstreamMcpToolResult> downstreamCalls
) {
}

