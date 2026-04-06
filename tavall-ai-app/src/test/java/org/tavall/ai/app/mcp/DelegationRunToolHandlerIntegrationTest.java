package org.tavall.ai.app.mcp;

import org.tavall.ai.app.mcp.tools.orchestration.DelegationRunToolHandler;
import org.tavall.ai.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DelegationRunToolHandlerIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private DelegationRunToolHandler handler;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void shouldRegisterDelegationRunTools() {
    Set<String> handlerTools = McpToolHandlerIntegrationAssertions.handlerToolNames(handler);
    Set<String> serverTools = McpToolHandlerIntegrationAssertions.serverToolNames(mcpSyncServer);

    McpToolHandlerIntegrationAssertions.assertContainsAll(
        handlerTools,
        "startDelegationRun",
        "appendDelegationRunEvent",
        "loadDelegationRun",
        "listDelegationRuns",
        "completeDelegationRun"
    );
    McpToolHandlerIntegrationAssertions.assertContainsAll(serverTools, "startDelegationRun", "loadDelegationRun");
  }
}

