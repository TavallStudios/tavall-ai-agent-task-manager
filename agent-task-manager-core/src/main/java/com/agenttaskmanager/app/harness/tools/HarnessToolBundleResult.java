package com.agenttaskmanager.app.harness.tools;

import com.agenttaskmanager.app.mcp.DownstreamMcpToolResult;
import java.util.List;
import java.util.Map;

public record HarnessToolBundleResult(
    String bundleName,
    Map<String, Object> summary,
    Map<String, Object> sections,
    List<DownstreamMcpToolResult> downstreamCalls
) {
}
