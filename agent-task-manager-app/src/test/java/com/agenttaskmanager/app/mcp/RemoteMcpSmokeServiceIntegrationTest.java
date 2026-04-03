package com.agenttaskmanager.app.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.cli.RemoteMcpSmokeResult;
import com.agenttaskmanager.app.cli.RemoteMcpSmokeService;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "app.security.mcp-no-auth-enabled=true"
})
class RemoteMcpSmokeServiceIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private RemoteMcpSmokeService remoteMcpSmokeService;

  @LocalServerPort
  private int port;

  @Test
  void shouldUseOfficialJavaClientAgainstLocalMcpServer() {
    RemoteMcpSmokeResult result = remoteMcpSmokeService.runSmoke(
        "http://127.0.0.1:" + port,
        "/mcp",
        "test-agent",
        ""
    );

    assertTrue(result.protocolVersion().startsWith("2025-"));
    assertEquals("AgentTaskManager MCP", result.serverName());
    assertTrue(result.toolNames().contains("createTaskBatch"));
    assertTrue(result.dashboardSummary().containsKey("queuedTasks"));
  }
}
