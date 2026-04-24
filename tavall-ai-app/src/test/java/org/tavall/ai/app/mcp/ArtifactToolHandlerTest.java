package org.tavall.ai.app.mcp;

import org.tavall.ai.app.mcp.tools.artifact.ArtifactToolHandler;
import org.tavall.ai.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ArtifactToolHandlerTest extends IntegrationTestSupport {

  @Autowired
  private ArtifactToolHandler handler;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void shouldRegisterArtifactTools() {
    Set<String> handlerTools = McpToolHandlerIntegrationAssertions.handlerToolNames(handler);
    Set<String> serverTools = McpToolHandlerIntegrationAssertions.serverToolNames(mcpSyncServer);

    McpToolHandlerIntegrationAssertions.assertContainsAll(
        handlerTools,
        "readArtifact",
        "writeArtifact",
        "storeTaskArtifact",
        "loadTaskArtifacts",
        "storeDiffArtifact",
        "loadDiffArtifact"
    );
    McpToolHandlerIntegrationAssertions.assertContainsAll(serverTools, "readArtifact", "writeArtifact", "storeDiffArtifact");
  }
}

