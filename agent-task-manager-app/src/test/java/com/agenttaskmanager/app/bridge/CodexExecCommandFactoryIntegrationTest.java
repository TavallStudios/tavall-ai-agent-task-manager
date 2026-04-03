package com.agenttaskmanager.app.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    "app.codex.downstream-central-server=agent-task-manager",
    "app.codex.required-mcp-servers=filesystem,qdrant",
    "app.codex.add-directories=/srv",
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
    assertFalse(command.stream().anyMatch(item -> item.contains("mcp_servers.qdrant.command=")));
    assertFalse(command.stream().anyMatch(item -> item.contains("mcp_servers.clean-java-harness.command=")));
    assertFalse(command.stream().anyMatch(item -> item.contains("mcp_servers.filesystem-localpc.command=")));
    assertFalse(command.stream().anyMatch(item -> item.contains("mcp_servers.memory.env.MEMORY_FILE_PATH=")));
    assertFalse(command.stream().anyMatch(item -> item.contains("mcp_servers.qdrant.env.COLLECTION_NAME=")));
    assertFalse(command.stream().anyMatch(item -> item.contains("mcp_servers.clean-java-mcp.command=")));
    assertTrue(command.stream().anyMatch(item -> item.contains("mcp_servers.agent-task-manager.command=\"java\"")));
    assertTrue(command.stream().anyMatch(item -> item.contains("mcp_servers.agent-task-manager.args=")));
    assertEquals(
        1,
        command.stream().filter(item -> item.contains("mcp_servers.") && item.contains(".command=")).count()
    );
    assertTrue(command.stream().anyMatch(item -> item.contains("serve-mcp-stdio")));
    assertTrue(command.contains("--add-dir"));
    assertTrue(command.contains("/srv"));
  }

  @Test
  void shouldEmbedSharedToolCombinationAndResponseGuidanceInPromptEnvelopes() {
    String envelope = commandFactory.buildPromptEnvelope(
        "edit",
        "Fix failing Java build and update service wiring",
        "Relevant memory"
    );

    assertTrue(envelope.contains("Tool combination patterns:"));
    assertTrue(envelope.contains("runHarnessToolBundle(worker-context)"));
    assertTrue(envelope.contains("runHarnessToolBundle(repo-context)"));
    assertTrue(envelope.contains("planGitCommit + prepareGitBranch + createGitCommit"));
    assertTrue(envelope.contains("loadTaskContext + loadValidationHistory + searchPriorFixes"));
    assertTrue(envelope.contains("local clean Java validation runs after worker execution"));
    assertTrue(envelope.contains("Contextual tool policy:"));
    assertTrue(envelope.contains("Contextual tool policy (auto-inferred):"));
    assertTrue(envelope.contains("decision: REQUIRED"));
    assertTrue(envelope.contains("required sequence:"));
    assertTrue(envelope.contains("Final response contract:"));
    assertTrue(envelope.contains("report verification status explicitly"));
  }
}
