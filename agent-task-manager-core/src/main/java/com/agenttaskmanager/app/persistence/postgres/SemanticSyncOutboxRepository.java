package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.retrieval.SemanticDocumentRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SemanticSyncOutboxRepository {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  public SemanticSyncOutboxRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
  }

  public SemanticSyncOutboxEntry enqueueProjectUpsert(String projectKey, SemanticDocumentRequest request, String dedupeKey) {
    return enqueueUpsert("project-upsert", projectKey, request, dedupeKey);
  }

  public SemanticSyncOutboxEntry enqueueKnowledgeUpsert(String knowledgeBase, SemanticDocumentRequest request, String dedupeKey) {
    return enqueueUpsert("knowledge-upsert", knowledgeBase, request, dedupeKey);
  }

  public SemanticSyncOutboxEntry enqueueProjectDelete(String projectKey, Map<String, Object> payloadFilter, String dedupeKey) {
    return enqueueDelete("project-delete", projectKey, payloadFilter, dedupeKey);
  }

  public SemanticSyncOutboxEntry enqueueKnowledgeDelete(String knowledgeBase, Map<String, Object> payloadFilter, String dedupeKey) {
    return enqueueDelete("knowledge-delete", knowledgeBase, payloadFilter, dedupeKey);
  }

  public List<SemanticSyncOutboxEntry> claimBatch(int limit) {
    return jdbcClient.sql("""
            WITH claimed AS (
              SELECT outbox_id
              FROM agent_task_manager.semantic_sync_outbox
              WHERE status = 'queued'
                AND available_at <= now()
              ORDER BY created_at ASC
              LIMIT :limit
              FOR UPDATE SKIP LOCKED
            )
            UPDATE agent_task_manager.semantic_sync_outbox AS outbox
            SET status = 'in_progress',
                attempt_count = outbox.attempt_count + 1
            FROM claimed
            WHERE outbox.outbox_id = claimed.outbox_id
            RETURNING outbox.*
            """)
        .param("limit", limit)
        .query(this::mapEntry)
        .list();
  }

  public void markCompleted(String outboxId) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.semantic_sync_outbox
            SET status = 'completed',
                last_error = NULL,
                completed_at = now()
            WHERE outbox_id = :outboxId
            """)
        .param("outboxId", outboxId)
        .update();
  }

  public void markQueued(String outboxId, String errorMessage, OffsetDateTime availableAt) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.semantic_sync_outbox
            SET status = 'queued',
                last_error = :errorMessage,
                available_at = :availableAt,
                completed_at = NULL
            WHERE outbox_id = :outboxId
            """)
        .param("outboxId", outboxId)
        .param("errorMessage", blank(errorMessage))
        .param("availableAt", availableAt)
        .update();
  }

  public long countPending() {
    Long count = jdbcClient.sql("""
            SELECT count(*)
            FROM agent_task_manager.semantic_sync_outbox
            WHERE status = 'queued'
            """)
        .query(Long.class)
        .single();
    return count == null ? 0 : count;
  }

  private SemanticSyncOutboxEntry enqueueUpsert(
      String operationKind,
      String scopeKey,
      SemanticDocumentRequest request,
      String dedupeKey
  ) {
    return upsertEntry(
        dedupeKey,
        operationKind,
        scopeKey,
        blank(request.documentId()),
        blank(request.taskId()),
        blank(request.workerTaskId()),
        blank(request.kind()),
        blank(request.title()),
        blank(request.content()),
        request.domain().name(),
        request.contentType().name(),
        request.payload() == null ? Map.of() : request.payload(),
        Map.of()
    );
  }

  private SemanticSyncOutboxEntry enqueueDelete(
      String operationKind,
      String scopeKey,
      Map<String, Object> payloadFilter,
      String dedupeKey
  ) {
    return upsertEntry(
        dedupeKey,
        operationKind,
        scopeKey,
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        Map.of(),
        payloadFilter == null ? Map.of() : payloadFilter
    );
  }

  private SemanticSyncOutboxEntry upsertEntry(
      String dedupeKey,
      String operationKind,
      String scopeKey,
      String documentId,
      String taskId,
      String workerTaskId,
      String semanticKind,
      String title,
      String content,
      String domain,
      String contentType,
      Map<String, Object> payload,
      Map<String, Object> payloadFilter
  ) {
    String outboxId = "sync_" + UUID.randomUUID();
    String effectiveDedupeKey = blank(dedupeKey);
    String sql = effectiveDedupeKey.isBlank()
        ? """
            INSERT INTO agent_task_manager.semantic_sync_outbox (
              outbox_id,
              dedupe_key,
              operation_kind,
              scope_key,
              document_id,
              task_id,
              worker_task_id,
              semantic_kind,
              title,
              content,
              domain,
              content_type,
              payload,
              payload_filter
            ) VALUES (
              :outboxId,
              NULL,
              :operationKind,
              :scopeKey,
              NULLIF(:documentId, ''),
              NULLIF(:taskId, ''),
              NULLIF(:workerTaskId, ''),
              NULLIF(:semanticKind, ''),
              NULLIF(:title, ''),
              NULLIF(:content, ''),
              NULLIF(:domain, ''),
              NULLIF(:contentType, ''),
              CAST(:payload AS jsonb),
              CAST(:payloadFilter AS jsonb)
            )
            RETURNING *
            """
        : """
            INSERT INTO agent_task_manager.semantic_sync_outbox (
              outbox_id,
              dedupe_key,
              operation_kind,
              scope_key,
              document_id,
              task_id,
              worker_task_id,
              semantic_kind,
              title,
              content,
              domain,
              content_type,
              payload,
              payload_filter
            ) VALUES (
              :outboxId,
              :dedupeKey,
              :operationKind,
              :scopeKey,
              NULLIF(:documentId, ''),
              NULLIF(:taskId, ''),
              NULLIF(:workerTaskId, ''),
              NULLIF(:semanticKind, ''),
              NULLIF(:title, ''),
              NULLIF(:content, ''),
              NULLIF(:domain, ''),
              NULLIF(:contentType, ''),
              CAST(:payload AS jsonb),
              CAST(:payloadFilter AS jsonb)
            )
            ON CONFLICT (dedupe_key) DO UPDATE SET
              operation_kind = EXCLUDED.operation_kind,
              scope_key = EXCLUDED.scope_key,
              document_id = EXCLUDED.document_id,
              task_id = EXCLUDED.task_id,
              worker_task_id = EXCLUDED.worker_task_id,
              semantic_kind = EXCLUDED.semantic_kind,
              title = EXCLUDED.title,
              content = EXCLUDED.content,
              domain = EXCLUDED.domain,
              content_type = EXCLUDED.content_type,
              payload = EXCLUDED.payload,
              payload_filter = EXCLUDED.payload_filter,
              status = 'queued',
              last_error = NULL,
              available_at = now(),
              completed_at = NULL
            RETURNING *
            """;
    return jdbcClient.sql(sql)
        .param("outboxId", outboxId)
        .param("dedupeKey", effectiveDedupeKey)
        .param("operationKind", operationKind)
        .param("scopeKey", scopeKey)
        .param("documentId", documentId)
        .param("taskId", taskId)
        .param("workerTaskId", workerTaskId)
        .param("semanticKind", semanticKind)
        .param("title", title)
        .param("content", content)
        .param("domain", domain)
        .param("contentType", contentType)
        .param("payload", writeJson(payload))
        .param("payloadFilter", writeJson(payloadFilter))
        .query(this::mapEntry)
        .single();
  }

  private SemanticSyncOutboxEntry mapEntry(ResultSet rs, int rowNum) throws SQLException {
    return new SemanticSyncOutboxEntry(
        rs.getString("outbox_id"),
        rs.getString("dedupe_key"),
        rs.getString("operation_kind"),
        rs.getString("scope_key"),
        rs.getString("document_id"),
        rs.getString("task_id"),
        rs.getString("worker_task_id"),
        rs.getString("semantic_kind"),
        rs.getString("title"),
        rs.getString("content"),
        rs.getString("domain"),
        rs.getString("content_type"),
        readJson(rs.getString("payload")),
        readJson(rs.getString("payload_filter")),
        rs.getString("status"),
        rs.getInt("attempt_count"),
        rs.getString("last_error"),
        rs.getObject("available_at", OffsetDateTime.class)
    );
  }

  private Map<String, Object> readJson(String value) throws SQLException {
    try {
      if (value == null || value.isBlank()) {
        return Map.of();
      }
      return objectMapper.readValue(value, MAP_TYPE);
    } catch (IOException exception) {
      throw new SQLException("Failed to deserialize semantic sync payload.", exception);
    }
  }

  private String writeJson(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to serialize semantic sync payload.", exception);
    }
  }

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }
}
