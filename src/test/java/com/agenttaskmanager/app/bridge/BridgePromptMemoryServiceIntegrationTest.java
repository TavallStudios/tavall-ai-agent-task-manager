package com.agenttaskmanager.app.bridge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "app.qdrant.project-collection-prefix=agent_task_manager_bridge_memory_test",
    "app.embedding.provider-order=hash",
    "app.embedding.dimensions=32"
})
class BridgePromptMemoryServiceIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private SharedTaskContextService sharedTaskContextService;

  @Autowired
  private BridgePromptMemoryService bridgePromptMemoryService;

  @Test
  void shouldInjectRetrievedMemoryIntoThePromptEnvelope() {
    String suffix = UUID.randomUUID().toString();
    sharedTaskContextService.storeTaskEmbedding(
        "bridge-test",
        "task-" + suffix,
        "worker-" + suffix,
        "prompt-memory",
        "Always check memory first for prompt " + suffix,
        Map.of("scope", "bridge-memory")
    );

    BridgePromptMemoryService.PreparedPrompt preparedPrompt = bridgePromptMemoryService.preparePrompt(
        "bridge-test",
        "edit",
        "Please investigate memory-first prompt handling " + suffix
    );

    assertTrue(preparedPrompt.memorySummary().contains("Memory lookup completed."));
    assertTrue(preparedPrompt.envelope().contains("Memory policy:"));
    assertTrue(preparedPrompt.envelope().contains("Always check memory first for prompt " + suffix));
    assertTrue(preparedPrompt.envelope().contains("while checking the prompt"));
  }
}
