package org.tavall.ai.app.memory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.model.PromptRequestSummary;
import org.tavall.ai.app.service.PromptRequestService;
import org.tavall.ai.app.support.IntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class MemoryRuntimeServiceTest extends IntegrationTestSupport {

  @Autowired
  private JdbcClient jdbcClient;

  @Autowired
  private MemoryContinuityService continuityService;

  @Autowired
  private MemoryRuntimeService memoryRuntimeService;

  @Autowired
  private MemoryRetrievalService memoryRetrievalService;

  @Autowired
  private PromptRequestService promptRequestService;

  @BeforeEach
  void cleanup() {
    jdbcClient.sql("DELETE FROM agent_task_manager.memory_mutations").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.memory_runtime_events").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.memory_continuity_snapshots").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.memory_records").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.prompt_messages").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.prompt_runs").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.prompt_threads").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.prompt_requests").update();
  }

  @Test
  void shouldPersistCanonicalMemoryAndContinuitySnapshotEveryTurn() {
    PromptRequestSummary request = promptRequestService.create(
        "memory-project",
        "/srv/test",
        "mcp-http",
        "edit",
        "Please remember that I prefer deterministic Java migrations.",
        "tester",
        "mcp-http"
    );
    MemoryTurnHandle handle = memoryRuntimeService.beginTurn(
        request.requestId(),
        "memory-project",
        "shared-thread",
        "session-1",
        "tester",
        "mcp-http",
        "/srv/test",
        "Please remember that I prefer deterministic Java migrations.",
        "deterministic Java migrations",
        Map.of("projectKey", "memory-project", "threadKey", "shared-thread")
    );

    Map<String, Object> outcome = memoryRuntimeService.completeTurn(
        handle,
        "I will keep deterministic Java migrations and preserve the current harness path.",
        false
    );

    assertFalse(handle.hydration().summary().isBlank());
    assertFalse(outcome.isEmpty());
    assertFalse(memoryRetrievalService.loadExactState(handle.identity()).isEmpty());
    assertTrue(continuityService.bootstrap(handle.identity()).isPresent());
  }
}

