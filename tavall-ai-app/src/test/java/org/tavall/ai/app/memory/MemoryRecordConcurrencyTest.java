package org.tavall.ai.app.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.tavall.ai.app.support.IntegrationTestSupport;

class MemoryRecordConcurrencyTest extends IntegrationTestSupport {

  @Autowired
  private JdbcClient jdbcClient;

  @Autowired
  private MemoryRecordService memoryRecordService;

  @Autowired
  private MemoryRetrievalService memoryRetrievalService;

  @BeforeEach
  void cleanup() {
    jdbcClient.sql("DELETE FROM agent_task_manager.semantic_sync_outbox").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.memory_mutations").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.memory_runtime_events").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.memory_continuity_snapshots").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.memory_records").update();
  }

  @Test
  void shouldSerializeConcurrentWritesToOneStableMemoryIdentity() throws Exception {
    MemoryIdentity identity = identity("concurrency-project", "concurrency-thread", "concurrency-session");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<MemoryRecord> first = executor.submit(() -> concurrentWrite(
          ready,
          start,
          identity,
          "issue://concurrency/first"
      ));
      Future<MemoryRecord> second = executor.submit(() -> concurrentWrite(
          ready,
          start,
          identity,
          "issue://concurrency/second"
      ));

      assertTrue(ready.await(Duration.ofSeconds(5)));
      start.countDown();

      MemoryRecord firstResult = first.get();
      MemoryRecord secondResult = second.get();

      assertEquals(firstResult.memoryId(), secondResult.memoryId());
      assertEquals(
          1L,
          jdbcClient.sql("""
                  SELECT count(*)
                  FROM agent_task_manager.memory_records
                  WHERE user_id = :userId
                    AND workspace_id = :workspaceId
                    AND project_id = :projectId
                    AND scope = 'PROJECT'
                    AND kind = 'PROJECT_STATE'
                    AND title_key = 'concurrent-stable-fact'
                    AND status = 'active'
                    AND tombstoned = false
                    AND superseded_by IS NULL
                  """)
              .param("userId", identity.userId())
              .param("workspaceId", identity.workspaceId())
              .param("projectId", identity.projectId())
              .query(Long.class)
              .single()
      );

      MemoryRecord active = memoryRetrievalService.loadExactState(identity).stream()
          .filter(record -> "concurrent-stable-fact".equals(record.titleKey()))
          .findFirst()
          .orElseThrow();
      assertEquals(2, active.version());
      assertTrue(active.sourceEventIds().contains("issue://concurrency/first"));
      assertTrue(active.sourceEventIds().contains("issue://concurrency/second"));
    }
  }

  @Test
  void shouldRejectSessionSupersessionFromAnotherThread() {
    MemoryIdentity originalIdentity = identity("session-project", "thread-a", "session-a");
    MemoryIdentity otherThread = identity("session-project", "thread-b", "session-b");
    MemoryRecord original = memoryRecordService.record(originalIdentity, write(
        MemoryScope.SESSION,
        "Session-local fact",
        "This fact belongs to thread A.",
        "issue://session/original",
        null
    ));

    assertThrows(IllegalArgumentException.class, () -> memoryRecordService.record(otherThread, write(
        MemoryScope.SESSION,
        "Session-local replacement",
        "Thread B cannot replace thread A session memory.",
        "issue://session/replacement",
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

  private MemoryRecord concurrentWrite(
      CountDownLatch ready,
      CountDownLatch start,
      MemoryIdentity identity,
      String sourceReference
  ) throws InterruptedException {
    ready.countDown();
    start.await();
    return memoryRecordService.record(identity, write(
        MemoryScope.PROJECT,
        "Concurrent stable fact",
        "Concurrent writers must converge on one active stable memory.",
        sourceReference,
        null
    ));
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
        "explicit",
        sourceReference,
        supersedesMemoryId,
        Map.of("reason", "memory-concurrency-regression")
    );
  }
}
