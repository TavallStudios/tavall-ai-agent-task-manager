package org.tavall.ai.app.persistence.postgres;

import org.tavall.ai.app.memory.MemoryIdentity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MemoryEventRepository {

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  public MemoryEventRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
  }

  public String recordEvent(
      String requestId,
      String phase,
      String eventKind,
      String idempotencyKey,
      MemoryIdentity identity,
      String summary,
      Map<String, Object> payload
  ) {
    String eventId = "mev_" + UUID.randomUUID();
    return jdbcClient.sql("""
            INSERT INTO agent_task_manager.memory_runtime_events (
              event_id,
              request_id,
              phase,
              event_kind,
              idempotency_key,
              user_id,
              workspace_id,
              api_key_id,
              session_id,
              chat_id,
              project_id,
              thread_key,
              summary,
              event_payload
            ) VALUES (
              :eventId,
              NULLIF(:requestId, ''),
              :phase,
              :eventKind,
              :idempotencyKey,
              :userId,
              :workspaceId,
              NULLIF(:apiKeyId, ''),
              NULLIF(:sessionId, ''),
              :chatId,
              :projectId,
              :threadKey,
              :summary,
              CAST(:payload AS jsonb)
            )
            ON CONFLICT (idempotency_key) DO UPDATE SET
              summary = EXCLUDED.summary,
              event_payload = EXCLUDED.event_payload,
              updated_at = now()
            RETURNING event_id
            """)
        .param("eventId", eventId)
        .param("requestId", blank(requestId))
        .param("phase", phase)
        .param("eventKind", eventKind)
        .param("idempotencyKey", idempotencyKey)
        .param("userId", blank(identity.userId()))
        .param("workspaceId", blank(identity.workspaceId()))
        .param("apiKeyId", blank(identity.apiKeyId()))
        .param("sessionId", blank(identity.sessionId()))
        .param("chatId", blank(identity.chatId()))
        .param("projectId", blank(identity.projectId()))
        .param("threadKey", blank(identity.threadKey()))
        .param("summary", blank(summary))
        .param("payload", writeJson(payload))
        .query(String.class)
        .single();
  }

  public void recordMutation(
      String requestId,
      String eventId,
      String memoryId,
      String action,
      String decisionSummary,
      String dedupeKey,
      Map<String, Object> payload
  ) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.memory_mutations (
              mutation_id,
              request_id,
              event_id,
              memory_id,
              action,
              decision_summary,
              dedupe_key,
              payload
            ) VALUES (
              :mutationId,
              NULLIF(:requestId, ''),
              NULLIF(:eventId, ''),
              NULLIF(:memoryId, ''),
              :action,
              :decisionSummary,
              :dedupeKey,
              CAST(:payload AS jsonb)
            )
            ON CONFLICT (dedupe_key) DO UPDATE SET
              decision_summary = EXCLUDED.decision_summary,
              payload = EXCLUDED.payload
            """)
        .param("mutationId", "mmu_" + UUID.randomUUID())
        .param("requestId", blank(requestId))
        .param("eventId", blank(eventId))
        .param("memoryId", blank(memoryId))
        .param("action", action)
        .param("decisionSummary", blank(decisionSummary))
        .param("dedupeKey", dedupeKey)
        .param("payload", writeJson(payload))
        .update();
  }

  public Map<String, Object> latestMutationSummary(String requestId) {
    return jdbcClient.sql("""
            SELECT payload
            FROM agent_task_manager.memory_mutations
            WHERE request_id = :requestId
            ORDER BY created_at DESC
            LIMIT 1
            """)
        .param("requestId", requestId)
        .query(String.class)
        .optional()
        .map(this::readJson)
        .orElse(Map.of());
  }

  public OffsetDateTime latestEventTime(String requestId) {
    return jdbcClient.sql("""
            SELECT max(updated_at)
            FROM agent_task_manager.memory_runtime_events
            WHERE request_id = :requestId
            """)
        .param("requestId", requestId)
        .query(OffsetDateTime.class)
        .optional()
        .orElse(null);
  }

  private String writeJson(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to serialize memory payload.", exception);
    }
  }

  private Map<String, Object> readJson(String value) {
    try {
      return objectMapper.readValue(value, new TypeReference<>() {
      });
    } catch (Exception exception) {
      return Map.of();
    }
  }

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }
}

