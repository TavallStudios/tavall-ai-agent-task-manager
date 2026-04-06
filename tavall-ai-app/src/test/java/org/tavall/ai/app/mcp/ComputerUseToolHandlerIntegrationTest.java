package org.tavall.ai.app.mcp;

import org.tavall.ai.app.mcp.tools.computeruse.ComputerUseToolHandler;
import org.tavall.ai.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ComputerUseToolHandlerIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private ComputerUseToolHandler handler;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void shouldRegisterComputerUseTools() {
    Set<String> handlerTools = McpToolHandlerIntegrationAssertions.handlerToolNames(handler);
    Set<String> serverTools = McpToolHandlerIntegrationAssertions.serverToolNames(mcpSyncServer);

    McpToolHandlerIntegrationAssertions.assertContainsAll(
        handlerTools,
        "registerComputerUseRunner",
        "listComputerUseRunners",
        "startComputerUseSession",
        "launchComputerUseProcess",
        "captureComputerUseWindow",
        "sendComputerUseInput",
        "waitForComputerUseVisionMatch",
        "stopComputerUseSession"
    );
    McpToolHandlerIntegrationAssertions.assertContainsAll(serverTools, "registerComputerUseRunner", "startComputerUseSession");
  }
}

