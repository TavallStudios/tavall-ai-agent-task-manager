package org.tavall.ai.app.mcp;

import org.tavall.ai.app.mcp.tools.vectormemory.VectorMemoryToolHandler;
import org.tavall.ai.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class VectorMemoryToolHandlerIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private VectorMemoryToolHandler handler;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void shouldRegisterVectorMemoryTools() {
    Set<String> handlerTools = McpToolHandlerIntegrationAssertions.handlerToolNames(handler);
    Set<String> serverTools = McpToolHandlerIntegrationAssertions.serverToolNames(mcpSyncServer);

    McpToolHandlerIntegrationAssertions.assertContainsAll(
        handlerTools,
        "storeSemanticDocument",
        "searchSemanticChunks",
        "searchSemanticHistory",
        "searchPromptThreads",
        "searchPromptThreadMemory",
        "searchKnowledgeIndex",
        "reindexSemanticKnowledge",
        "reindexConfiguredCodebases",
        "attachSemanticDocumentToTask",
        "purgeLegacySemanticCollection"
    );
    McpToolHandlerIntegrationAssertions.assertContainsAll(serverTools, "storeSemanticDocument", "searchKnowledgeIndex");
  }
}

