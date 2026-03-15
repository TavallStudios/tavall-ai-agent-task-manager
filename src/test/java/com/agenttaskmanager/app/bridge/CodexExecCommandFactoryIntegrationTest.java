package com.agenttaskmanager.app.bridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.support.IntegrationTestSupport;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "app.codex.mcp-server-bin-dir=/srv/test-mcp/bin",
    "app.codex.memory-file-path=/srv/test-memory.jsonl",
    "app.codex.add-directories=/srv,/srv/local-pc-root",
    "app.qdrant.project-collection-prefix=agent_task_manager_project_test"
})
class CodexExecCommandFactoryIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private CodexExecCommandFactory commandFactory;

  @Test
  void shouldInjectDeterministicCodexConfigOverrides() {
    List<String> command = commandFactory.buildCommand(
        "hy-rhythm",
        Path.of("/srv/AgentTaskManager"),
        "edit",
        Path.of("/tmp/agent-task-manager-output.txt"),
        null
    );

    assertTrue(command.contains("-c"));
    assertTrue(command.stream().anyMatch(item -> item.contains("mcp_servers.filesystem.command=\"/srv/test-mcp/bin/filesystem\"")));
    assertFalse(command.stream().anyMatch(item -> item.contains("mcp_servers.filesystem-localpc.command=")));
    assertTrue(command.stream().anyMatch(item -> item.contains("mcp_servers.memory.env.MEMORY_FILE_PATH=\"/srv/test-memory.jsonl\"")));
    assertTrue(command.stream().anyMatch(item -> item.contains("mcp_servers.qdrant.env.COLLECTION_NAME=\"agent_task_manager_project_test_hy_rhythm\"")));
    assertTrue(command.stream().anyMatch(item -> item.contains("mcp_servers.clean-java-mcp.command=\"/bin/bash\"")));
    assertTrue(command.stream().anyMatch(item -> item.contains("mcp_servers.clean-java-harness.command=\"/bin/bash\"")));
    assertTrue(command.contains("--add-dir"));
    assertTrue(command.contains("/srv/local-pc-root"));
  }
}
