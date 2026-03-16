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
    assertFalse(command.stream().anyMatch(item -> item.contains("mcp_servers.filesystem.command=")));
    assertFalse(command.stream().anyMatch(item -> item.contains("mcp_servers.filesystem-localpc.command=")));
    assertFalse(command.stream().anyMatch(item -> item.contains("mcp_servers.memory.env.MEMORY_FILE_PATH=")));
    assertFalse(command.stream().anyMatch(item -> item.contains("mcp_servers.qdrant.env.COLLECTION_NAME=")));
    assertFalse(command.stream().anyMatch(item -> item.contains("mcp_servers.clean-java-mcp.command=")));
    assertTrue(command.stream().anyMatch(item -> item.contains("mcp_servers.clean-java-harness.command=\"java\"")));
    assertTrue(command.stream().anyMatch(item -> item.contains(
        "mcp_servers.clean-java-harness.args=[\"-jar\",\"/srv/AgentTaskManager/agent-task-manager-clean-java-harness/target/agent-task-manager-clean-java-harness-0.1.0-SNAPSHOT-exec.jar\"]"
    )));
    assertTrue(command.contains("--add-dir"));
    assertTrue(command.contains("/srv/local-pc-root"));
  }

  @Test
  void shouldEmbedSharedToolCombinationAndResponseGuidanceInPromptEnvelopes() {
    String envelope = commandFactory.buildPromptEnvelope(
        "edit",
        "Improve output quality",
        "Relevant memory"
    );

    assertTrue(envelope.contains("Tool combination patterns:"));
    assertTrue(envelope.contains("runHarnessToolBundle(worker-context)"));
    assertTrue(envelope.contains("runHarnessToolBundle(repo-context)"));
    assertTrue(envelope.contains("loadCleanJavaTaskContext + runHarnessToolBundle(java-context)"));
    assertTrue(envelope.contains("runCleanJavaHarness: run Spoon source-shape checks first"));
    assertTrue(envelope.contains("Final response contract:"));
    assertTrue(envelope.contains("report verification status explicitly"));
  }
}
