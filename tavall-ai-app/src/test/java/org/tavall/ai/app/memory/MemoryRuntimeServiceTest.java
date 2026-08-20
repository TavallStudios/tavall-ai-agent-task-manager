package org.tavall.ai.app.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    MemoryIdentity identity = identity("memory-project", "shared-thread", "session-1");

    MemoryRecord record = memoryRecordService.record(identity, write(
        MemoryScope.PROJECT,
        "Java migration policy",
        "Use deterministic Java migrations for this project.",
        "explicit",
        "issue://memory-project/42",
        null
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
  void shouldForceExplicitConsentOnExplicitWriter() {
    MemoryIdentity identity = identity("authority-project", "authority-thread", "authority-session");

    MemoryRecord record = memoryRecordService.record(identity, write(
        MemoryScope.PROJECT,
        "Explicit write authority",
        "Explicit recordMemory writes cannot relabel themselves as implicit.",
        "implicit",
        "issue://memory-project/authority",
        null
    ));

    assertEquals("explicit", record.consentLevel());
    assertEquals("explicit", record.metadata().get("writeMode"));
    assertEquals(
        0L,
        jdbcClient.sql("SELECT count(*) FROM agent_task_manager.memory_records WHERE consent_level = 'implicit'")
            .query(Long.class)
            .single()
    );
  }

  @Test
  void shouldRejectSupersedingProjectMemoryOutsideCurrentAuthorityScope() {
    MemoryIdentity projectA = identity("authority-project-a", "authority-thread-a", "authority-session-a");
    MemoryIdentity projectB = identity("authority-project-b", "authority-thread-b", "authority-session-b");
    MemoryRecord original = memoryRecordService.record(projectA, write(
        MemoryScope.PROJECT,
        "Project A authority",
        "This record belongs to project A.",
        "explicit",
        "issue://project-a/1",
        null
    ));

    assertThrows(IllegalArgumentException.class, () -> memoryRecordService.record(projectB, write(
        MemoryScope.PROJECT,
        "Project B replacement",
        "Project B must not supersede project A memory by id.",
        "explicit",
        "issue://project-b/1",
        original.memoryId()
    )));

    assertEquals(
        "active",
        jdbcClient.sql("SELECT status FROM agent_task_manager.memory_records WHERE memory_id = :memoryId")
            .param("memoryId", original.memoryId())
            .query(String.class)
            .single()
    );
    assertEquals(
        1L,
        jdbcClient.sql("SELECT count(*) FROM agent_task_manager.memory_records")
            .query(Long.class)
            .single()
    );
    assertEquals(1, semanticMemoryService.searchProject(
        "authority-project-a",
        "This record belongs to project A",
        10,
        Map.of("memoryId", original.memoryId())
    ).size());
  }

  @Test
  void shouldRejectSupersessionThatChangesMemoryScope() {
    MemoryIdentity identity = identity("scope-project", "scope-thread", "scope-session");
    MemoryRecord original = memoryRecordService.record(identity, write(
        MemoryScope.GLOBAL,
        "Global scope authority",
        "This record is intentionally global.",
        "explicit",
        "issue://scope/global",
        null
    ));

    assertThrows(IllegalArgumentException.class, () -> memoryRecordService.record(identity, write(
        MemoryScope.PROJECT,
        "Project scope replacement",
        "Supersession must not silently migrate scope.",
        "explicit",
        "issue://scope/project",
        original.memoryId()
    )));

    assertEquals(
        "active",
        jdbcClient.sql("SELECT status FROM agent_task_manager.memory_records WHERE memory_id = :memoryId")
            .param("memoryId", original.memoryId())
            .query(String.class)
            .single()
    );
    assertEquals(
        1L,
        jdbcClient.sql("SELECT count(*) FROM agent_task_manager.memory_records")
            .query(Long.class)
            .single()
    );
  }

  @Test
  void shouldSupersedeExplicitMemoryAndRemoveItsSemanticRepresentation() {
    MemoryIdentity identity = identity("supersession-project", "supersession-thread", "supersession-session");
    MemoryRecord original = memoryRecordService.record(identity, write(
        MemoryScope.PROJECT,
        "Runtime ownership",
        "The old runtime owns this memory.",
        "explicit",
        "issue://memory-project/old",
        null
    ));

    MemoryRecord replacement = memoryRecordService.record(identity, write(
        MemoryScope.PROJECT,
        "Runtime ownership replacement",
        "The new runtime owns this memory.",
        "explicit",
        "issue://memory-project/new",
        original.memoryId()
    ));

    assertEquals(
        "superseded",
        jdbcClient.sql("SELECT status FROM agent_task_manager.memory_records WHERE memory_id = :memoryId")
            .param("memoryId", original.memoryId())
            .query(String.class)
            .single()
    );
    assertTrue(semanticMemoryService.searchProject(
        "supersession-project",
        "The old runtime owns this memory",
        10,
        Map.of("memoryId", original.memoryId())
    ).isEmpty());
    var replacementSemantic = semanticMemoryService.searchProject(
        "supersession-project",
        "The new runtime owns this memory",
        10,
        Map.of("memoryId", replacement.memoryId())
    );
    assertEquals(1, replacementSemantic.size());
    assertEquals(replacement.memoryId(), replacementSemantic.getFirst().payload().get("memoryId"));
  }

  @Test
  void shouldInvalidateOtherProjectExactCachesWhenGlobalMemoryChanges() {
    MemoryIdentity projectA = identity("global-cache-project-a", "global-cache-thread-a", "global-cache-session-a");
    MemoryIdentity projectB = identity("global-cache-project-b", "global-cache-thread-b", "global-cache-session-b");

    assertTrue(memoryRetrievalService.loadExactState(projectB).isEmpty());

    MemoryRecord global = memoryRecordService.record(projectA, write(
        MemoryScope.GLOBAL,
        "Global cache revision",
        "A global memory write must invalidate exact-state cache views across projects.",
        "explicit",
        "issue://global/cache-revision",
        null
    ));

    List<MemoryRecord> projectBExact = memoryRetrievalService.loadExactState(projectB);
    assertEquals(1, projectBExact.size());
    assertEquals(global.memoryId(), projectBExact.getFirst().memoryId());
  }

  @Test
  void shouldDeleteGlobalMemoryFromItsOriginalSemanticProjectWhenSupersededElsewhere() {
    MemoryIdentity projectA = identity("global-semantic-project-a", "global-semantic-thread-a", "global-semantic-session-a");
    MemoryIdentity projectB = identity("global-semantic-project-b", "global-semantic-thread-b", "global-semantic-session-b");
    MemoryRecord original = memoryRecordService.record(projectA, write(
        MemoryScope.GLOBAL,
        "Global runtime fact",
        "This global fact was first recorded while project A was active.",
        "explicit",
        "issue://global/old",
        null
    ));

    assertEquals("global-semantic-project-a", original.metadata().get("semanticProjectId"));
    assertEquals(1, semanticMemoryService.searchProject(
        "global-semantic-project-a",
        "global fact first recorded",
        10,
        Map.of("memoryId", original.memoryId())
    ).size());

    MemoryRecord replacement = memoryRecordService.record(projectB, write(
        MemoryScope.GLOBAL,
        "Global runtime fact replacement",
        "The global fact was superseded while project B was active.",
        "explicit",
        "issue://global/new",
        original.memoryId()
    ));

    assertTrue(semanticMemoryService.searchProject(
        "global-semantic-project-a",
        "global fact first recorded",
        10,
        Map.of("memoryId", original.memoryId())
    ).isEmpty());
    assertEquals("global-semantic-project-b", replacement.metadata().get("semanticProjectId"));
    assertEquals(1, semanticMemoryService.searchProject(
        "global-semantic-project-b",
        "global fact superseded",
        10,
        Map.of("memoryId", replacement.memoryId())
    ).size());
  }

  private MemoryIdentity identity(String projectId, String threadKey, String sessionId) {
    return memoryRetrievalService.resolveIdentity(
        projectId,
        threadKey,
        sessionId,
        "tester",
        "mcp-http",
        "/srv/test",
        Map.of("projectKey", projectId, "threadKey", threadKey)
    );
  }

  private MemoryWriteRequest write(
      MemoryScope scope,
      String title,
      String summary,
      String consentLevel,
      String sourceReference,
      String supersedesMemoryId
  ) {
    return new MemoryWriteRequest(
        scope,
        MemoryKind.PROJECT_STATE,
        title,
        summary,
        List.of(summary),
        90,
        "internal",
        consentLevel,
        sourceReference,
        supersedesMemoryId,
        Map.of("reason", "memory-runtime-regression")
    );
  }
}
