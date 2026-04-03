package com.agenttaskmanager.app.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "app.codex.downstream-central-server=agent-task-manager",
    "app.codex.central-server-local-stdio-enabled=true",
    "app.codex.remote-tool-execution-enabled=true",
    "app.mcp.base-url=https://docs.example.com/agent-task-manager",
    "app.mcp.endpoint=/mcp"
})
class McpServerProcessConfigurationServiceIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private McpServerProcessConfigurationService processConfigurationService;

  @Test
  void shouldLaunchCentralServerLocallyOverStdio() {
    McpServerProcessConfiguration configuration = processConfigurationService.resolve(
        "agent-task-manager",
        "project-novus"
    );

    assertEquals("java", configuration.command());
    assertTrue(configuration.args().contains("-jar"));
    assertTrue(configuration.args().contains("serve-mcp-stdio"));
    assertTrue(configuration.args().stream().anyMatch(arg -> arg.endsWith("agent-task-manager-app-0.1.0-SNAPSHOT.jar")));
    assertEquals("", configuration.env().get("AGENT_TASK_MANAGER_CODEX_DOWNSTREAM_CENTRAL_SERVER"));
    assertEquals("true", configuration.env().get("AGENT_TASK_MANAGER_CODEX_REMOTE_TOOL_EXECUTION_ENABLED"));
    assertEquals(
        "https://docs.example.com/agent-task-manager",
        configuration.env().get("AGENT_TASK_MANAGER_MCP_BASE_URL")
    );
    assertEquals("/mcp", configuration.env().get("AGENT_TASK_MANAGER_MCP_ENDPOINT"));
    assertEquals(
        "test-agent",
        configuration.env().get("AGENT_TASK_MANAGER_USERNAME")
    );
    assertEquals("test-password", configuration.env().get("AGENT_TASK_MANAGER_PASSWORD"));
  }

  @Test
  void shouldKeepFilesystemServerLocalWhenLocalCentralUsesRemoteHttpRepoBroker() {
    McpServerProcessConfiguration configuration = processConfigurationService.resolve("filesystem", "project-novus");

    assertEquals("filesystem", configuration.command());
    assertEquals(List.of(), configuration.args());
  }

  @Test
  void shouldRejectStandaloneHarnessServerResolution() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> processConfigurationService.resolve("clean-java-harness", "project-novus")
    );

    assertTrue(exception.getMessage().contains("no longer launched as an MCP server"));
  }
}
