package org.tavall.ai.app.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.tavall.ai.app.retrieval.SemanticMemoryService;
import org.tavall.ai.app.model.PromptRequestSummary;
import org.tavall.ai.app.service.PromptRequestService;
import org.tavall.ai.app.support.IntegrationTestSupport;

class MemoryRuntimeServiceTest extends IntegrationTestSupport {

  @Autowired
  private JdbcClient jdbcClient;

  @Autowired
  private MemoryContinuityService continuityService;

  @Autowired
  private MemoryRecordService memoryRecordService;

  @Autowired
  private MemoryRuntimeService memoryRuntimeService;

  @Autowired
  private MemoryRetrievalService memoryRetrievalService;

  @Autowired
  private SemanticMemoryService semanticMemoryService;

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
  void shouldRefreshContinuityWithoutInventingDurableMemoryFromTurnText() {
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
    assertEquals(Boolean.FALSE, outcome.get("durableMemoryMutation"));
    assertTrue(memoryRetrievalService.loadExactState(handle.identity()).isEmpty());
    assertTrue(continuityService.bootstrap(handle.identity()).isPresent());
  }

  @Test
  void shouldIgnoreNaturalLanguageMemoryTriggersWithoutExplicitWrite() {
    for (String trigger : List.of("remember", "prefer", "actually", "next", "migration")) {
      String projectKey = "memory-trigger-" + trigger;
      PromptRequestSummary request = promptRequestService.create(
          projectKey,
          "/srv/test",
          "mcp-http",
          "edit",
          "The ordinary turn contains the word " + trigger + ".",
          "tester",
          "mcp-http"
      );
      MemoryTurnHandle handle = memoryRuntimeService.beginTurn(
          request.requestId(),
          projectKey,
          "thread-" + trigger,
          "session-" + trigger,
          "tester",
          "mcp-http",
          "/srv/test",
          "The ordinary turn contains the word " + trigger + ".",
          "ordinary turn context",
          Map.of("projectKey", projectKey, "threadKey", "thread-" + trigger)
      );
      memoryRuntimeService.completeTurn(handle, "The turn completed without an explicit memory write.", false);
    }

    assertEquals(
        0L,
        jdbcClient.sql("SELECT count(*) FROM agent_task_manager.memory_records")
            .query(Long.class)
            .single()
    );
  }

  @Test
  void shouldPersistOnlyExplicitDistilledMemoryWithProvenance() {
    MemoryIdentity identity = memoryRetrievalService.resolveIdentity(
        "memory-project",
        "shared-thread",
        "session-1",
        "tester",
        "mcp-http",
        "/srv/test",
        Map.of("projectKey", "memory-project", "threadKey", "shared-thread")
    );

    MemoryRecord record = memoryRecordService.record(identity, new MemoryWriteRequest(
        MemoryScope.PROJECT,
        MemoryKind.PROJECT_STATE,
        "Java migration policy",
        "Use deterministic Java migrations for this project.",
        List.of("Preserve the current harness path while migrating."),
        90,
        "internal",
        "explicit",
        "issue://memory-project/42",
        null,
        Map.of("reason", "verified-project-rule")
    ));

    List<MemoryRecord> loaded = memoryRetrievalService.loadExactState(identity);
    assertEquals(1, loaded.size());
    assertEquals(record.memoryId(), loaded.getFirst().memoryId());
    assertEquals("Use deterministic Java migrations for this project.", loaded.getFirst().summary());
    assertEquals("issue://memory-project/42", loaded.getFirst().metadata().get("sourceReference"));
    assertEquals("explicit", loaded.getFirst().metadata().get("writeMode"));

    var semantic = semanticMemoryService.searchProject(
        "memory-project",
        "Use deterministic Java migrations",
        10,
        Map.of("memoryId", record.memoryId())
    );
    assertEquals(1, semantic.size());
    assertEquals(record.memoryId(), semantic.getFirst().payload().get("memoryId"));
    assertEquals("explicit", semantic.getFirst().payload().get("writeMode"));
  }

  @Test
  void shouldSupersedeExplicitMemoryAndRemoveItsSemanticRepresentation() {
    MemoryIdentity identity = memoryRetrievalService.resolveIdentity(
        "memory-project",
        "shared-thread",
        "session-1",
        "tester",
        "mcp-http",
        "/srv/test",
        Map.of("projectKey", "memory-project", "threadKey", "shared-thread")
    );
    MemoryRecord original = memoryRecordService.record(identity, new MemoryWriteRequest(
        MemoryScope.PROJECT,
        MemoryKind.PROJECT_STATE,
        "Runtime ownership",
        "The old runtime owns this memory.",
        List.of("Old ownership claim."),
        80,
        "internal",
        "explicit",
        "issue://memory-project/old",
        null,
        Map.of("reason", "initial-fact")
    ));

    MemoryRecord replacement = memoryRecordService.record(identity, new MemoryWriteRequest(
        MemoryScope.PROJECT,
        MemoryKind.PROJECT_STATE,
        "Runtime ownership replacement",
        "The new runtime owns this memory.",
        List.of("Replacement ownership claim."),
        90,
        "internal",
        "explicit",
        "issue://memory-project/new",
        original.memoryId(),
        Map.of("reason", "superseding-fact")
    ));

    assertEquals(
        "superseded",
        jdbcClient.sql("SELECT status FROM agent_task_manager.memory_records WHERE memory_id = :memoryId")
            .param("memoryId", original.memoryId())
            .query(String.class)
            .single()
    );
    assertTrue(semanticMemoryService.searchProject(
        "memory-project",
        "The old runtime owns this memory",
        10,
        Map.of("memoryId", original.memoryId())
    ).isEmpty());
    var replacementSemantic = semanticMemoryService.searchProject(
        "memory-project",
        "The new runtime owns this memory",
        10,
        Map.of("memoryId", replacement.memoryId())
    );
    assertEquals(1, replacementSemantic.size());
    assertEquals(replacement.memoryId(), replacementSemantic.getFirst().payload().get("memoryId"));
  }
}
