package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.mcp.tools.context.ContextToolHandler;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ContextToolHandlerIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private ContextToolHandler handler;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void shouldRegisterContextTools() {
    Set<String> handlerTools = McpToolHandlerIntegrationAssertions.handlerToolNames(handler);
    Set<String> serverTools = McpToolHandlerIntegrationAssertions.serverToolNames(mcpSyncServer);

    McpToolHandlerIntegrationAssertions.assertContainsAll(
        handlerTools,
        "loadTaskContext",
        "loadArchitectureRules",
        "loadExamples",
        "loadValidationHistory",
        "loadDashboardSummary",
        "loadChatState",
        "searchSemanticContext",
        "loadSiblingTaskSummaries",
        "storeSharedTaskContext",
        "loadSharedTaskContext"
    );
    McpToolHandlerIntegrationAssertions.assertContainsAll(serverTools, "loadTaskContext", "loadDashboardSummary");
  }
}
