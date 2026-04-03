package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.mcp.tools.vectormemory.VectorMemoryCanonicalToolHandler;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class VectorMemoryCanonicalToolHandlerIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private VectorMemoryCanonicalToolHandler handler;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void shouldRegisterCanonicalVectorMemoryTools() {
    Set<String> handlerTools = McpToolHandlerIntegrationAssertions.handlerToolNames(handler);
    Set<String> serverTools = McpToolHandlerIntegrationAssertions.serverToolNames(mcpSyncServer);

    McpToolHandlerIntegrationAssertions.assertContainsAll(
        handlerTools,
        "storeTaskEmbedding",
        "searchRelatedContexts",
        "loadRelatedSemanticContext",
        "searchPriorFixes",
        "attachSemanticContextToTask"
    );
    McpToolHandlerIntegrationAssertions.assertContainsAll(serverTools, "storeTaskEmbedding", "searchPriorFixes");
  }
}
