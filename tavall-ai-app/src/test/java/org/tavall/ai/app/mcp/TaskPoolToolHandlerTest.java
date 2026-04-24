package org.tavall.ai.app.mcp;

import org.tavall.ai.app.mcp.tools.orchestration.TaskPoolToolHandler;
import org.tavall.ai.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TaskPoolToolHandlerTest extends IntegrationTestSupport {

  @Autowired
  private TaskPoolToolHandler handler;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void shouldRegisterTaskPoolTools() {
    Set<String> handlerTools = McpToolHandlerIntegrationAssertions.handlerToolNames(handler);
    Set<String> serverTools = McpToolHandlerIntegrationAssertions.serverToolNames(mcpSyncServer);

    McpToolHandlerIntegrationAssertions.assertContainsAll(
        handlerTools,
        "createTaskBatch",
        "claimWorkerTask",
        "assignWorkerTask",
        "reassignWorkerTask",
        "completeWorkerTask",
        "failWorkerTask",
        "deadLetterWorkerTask",
        "createCleanupReviewTask"
    );
    McpToolHandlerIntegrationAssertions.assertContainsAll(serverTools, "createTaskBatch", "claimWorkerTask");
  }
}

