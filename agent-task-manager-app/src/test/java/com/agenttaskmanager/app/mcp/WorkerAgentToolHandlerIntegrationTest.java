package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.mcp.tools.orchestration.WorkerAgentToolHandler;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WorkerAgentToolHandlerIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private WorkerAgentToolHandler handler;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void shouldRegisterWorkerLifecycleTools() {
    Set<String> handlerTools = McpToolHandlerIntegrationAssertions.handlerToolNames(handler);
    Set<String> serverTools = McpToolHandlerIntegrationAssertions.serverToolNames(mcpSyncServer);

    McpToolHandlerIntegrationAssertions.assertContainsAll(
        handlerTools,
        "submitWorkerCheckIn",
        "heartbeatWorker",
        "markWorkerDead",
        "registerWorker",
        "updateWorkerLease",
        "registerCleanupAgent",
        "submitCleanupReview",
        "markCleanupReviewRequired",
        "markCleanupApproved",
        "markCleanupRejected"
    );
    McpToolHandlerIntegrationAssertions.assertContainsAll(serverTools, "submitWorkerCheckIn", "registerWorker");
  }
}
