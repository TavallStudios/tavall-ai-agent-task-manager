package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.mcp.tools.repo.SharedRepoSnapshotToolHandler;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SharedRepoSnapshotToolHandlerIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private SharedRepoSnapshotToolHandler handler;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void shouldRegisterRepoTransferTools() {
    Set<String> handlerTools = McpToolHandlerIntegrationAssertions.handlerToolNames(handler);
    Set<String> serverTools = McpToolHandlerIntegrationAssertions.serverToolNames(mcpSyncServer);

    McpToolHandlerIntegrationAssertions.assertContainsAll(handlerTools, "stageSharedRepoSnapshot");
    McpToolHandlerIntegrationAssertions.assertContainsAll(serverTools, "stageSharedRepoSnapshot");
  }
}
