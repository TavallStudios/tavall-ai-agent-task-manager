package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.mcp.tools.orchestration.DecisionToolHandler;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DecisionToolHandlerIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private DecisionToolHandler handler;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void shouldRegisterDecisionTools() {
    Set<String> handlerTools = McpToolHandlerIntegrationAssertions.handlerToolNames(handler);
    Set<String> serverTools = McpToolHandlerIntegrationAssertions.serverToolNames(mcpSyncServer);

    McpToolHandlerIntegrationAssertions.assertContainsAll(
        handlerTools,
        "mergeWorkerOutputs",
        "approvePatch",
        "rejectPatch",
        "storeOverseerDecision",
        "storeRunSummary",
        "runAutonomousCycle",
        "publishDashboardUpdate"
    );
    McpToolHandlerIntegrationAssertions.assertContainsAll(serverTools, "mergeWorkerOutputs", "approvePatch");
  }
}
