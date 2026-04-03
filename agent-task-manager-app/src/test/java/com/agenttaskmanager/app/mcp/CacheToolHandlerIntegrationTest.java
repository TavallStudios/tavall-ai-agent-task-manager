package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.mcp.tools.cache.CacheToolHandler;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CacheToolHandlerIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private CacheToolHandler handler;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void shouldRegisterCacheTools() {
    Set<String> handlerTools = McpToolHandlerIntegrationAssertions.handlerToolNames(handler);
    Set<String> serverTools = McpToolHandlerIntegrationAssertions.serverToolNames(mcpSyncServer);

    McpToolHandlerIntegrationAssertions.assertContainsAll(
        handlerTools,
        "cacheTaskContext",
        "getCachedTaskContext",
        "cacheValidationSummary",
        "getCachedValidationSummary",
        "invalidateTaskCache",
        "warmDashboardCache"
    );
    McpToolHandlerIntegrationAssertions.assertContainsAll(serverTools, "cacheTaskContext", "warmDashboardCache");
  }
}
