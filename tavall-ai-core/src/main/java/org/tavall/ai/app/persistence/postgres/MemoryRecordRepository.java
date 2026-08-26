package org.tavall.ai.app.persistence.postgres;

import org.tavall.ai.app.memory.MemoryIdentity;
import org.tavall.ai.app.memory.MemoryKind;
import org.tavall.ai.app.memory.MemoryMutationPlan;
import org.tavall.ai.app.memory.MemoryRecord;
import org.tavall.ai.app.memory.MemoryScope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MemoryRecordRepository {

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  public MemoryRecordRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
  }

  public List<MemoryRecord> loadExactState(MemoryIdentity identity, int limit) {
    return jdbcClient.sql("""
            SELECT *
            FROM agent_task_manager.memory_records
            WHERE status = 'active'
              AND tombstoned = false
              AND superseded_by IS NULL
              AND user_id = :userId
              AND workspace_id = :workspaceId
              AND (
                (:threadKey <> '' AND scope = 'SESSION' AND thread_key = :threadKey)
                OR (:projectId <> '' AND scope = 'PROJECT' AND project_id = :projectId)
                OR scope = 'GLOBAL'
              )
            ORDER BY
              CASE scope WHEN 'SESSION' THEN 0 WHEN 'PROJECT' THEN 1 ELSE 2 END,
              importance DESC,
              updated_at DESC
            LIMIT :limit
            """)
        .param("userId", blank(identity.userId()))
        .param("workspaceId", blank(identity.workspaceId()))
        .param("threadKey", blank(identity.threadKey()))
        .param("projectId", blank(identity.projectId()))
        .param("limit", limit)
        .query((rs, rowNum) -> mapRecord(
            rs.getString("memory_id"),
            rs.getString("user_id"),
            rs.getString("workspace_id"),
            rs.getString("session_id"),
            rs.getString("chat_id"),
            rs.getString("request_id"),
            rs.getString("project_id"),
            rs.getString("thread_key"),
            rs.getString("scope"),
            rs.getString("kind"),
            rs.getString("title"),
            rs.getString("title_key"),
            rs.getString("summary"),
            rs.getString("facts"),
            rs.getString("source_event_ids"),
            rs.getInt("version"),
            rs.getString("status"),
            rs.getInt("importance"),
            rs.getString("sensitivity"),
            rs.getString("consent_level"),
            rs.getString("superseded_by"),
            rs.getBoolean("tombstoned"),
            rs.getString("metadata"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        ))
        .list();
  }

  /**
   * Serializes canonical writers for one stable memory identity for the life of the current
   * PostgreSQL transaction. Postgres owns this lock because Postgres owns durable memory state.
   */
  public void lockStableRecordIdentity(MemoryIdentity identity, MemoryMutationPlan plan) {
    lockStableRecordKeys(List.of(stableRecordKey(identity, plan)));
  }

  /**
   * Acquires replacement and superseded stable-memory locks in deterministic order to avoid
   * cross-supersession lock inversion between concurrent agent writes.
   */
  public void lockStableRecordIdentities(
      MemoryIdentity identity,
      MemoryMutationPlan replacement,
      MemoryRecord superseded
  ) {
    lockStableRecordKeys(List.of(stableRecordKey(identity, replacement), stableRecordKey(superseded)));
  }

  public Optional<MemoryRecord> findStableRecord(MemoryIdentity identity, MemoryMutationPlan plan) {
    return jdbcClient.sql("""
            SELECT *
            FROM agent_task_manager.memory_records
            WHERE user_id = :userId
              AND workspace_id = :workspaceId
              AND project_id = :projectId
              AND chat_id = :chatId
              AND thread_key = :threadKey
              AND scope = :scope
              AND kind = :kind
              AND title_key = :titleKey
              AND status = 'active'
              AND tombstoned = false
              AND superseded_by IS NULL
            ORDER BY updated_at DESC
            LIMIT 1
            """)
        .param("userId", blank(identity.userId()))
        .param("workspaceId", blank(identity.workspaceId()))
        .param("projectId", blank(projectId(identity, plan.scope())))
        .param("chatId", blank(chatId(identity, plan.scope())))
        .param("threadKey", blank(threadKey(identity, plan.scope())))
        .param("scope", plan.scope().name())
        .param("kind", plan.kind().name())
        .param("titleKey", plan.titleKey())
        .query((rs, rowNum) -> mapRecord(
            rs.getString("memory_id"),
            rs.getString("user_id"),
            rs.getString("workspace_id"),
            rs.getString("session_id"),
            rs.getString("chat_id"),
            rs.getString("request_id"),
            rs.getString("project_id"),
            rs.getString("thread_key"),
            rs.getString("scope"),
            rs.getString("kind"),
            rs.getString("title"),
            rs.getString("title_key"),
            rs.getString("summary"),
            rs.getString("facts"),
            rs.getString("source_event_ids"),
            rs.getInt("version"),
            rs.getString("status"),
            rs.getInt("importance"),
            rs.getString("sensitivity"),
            rs.getString("consent_level"),
            rs.getString("superseded_by"),
            rs.getBoolean("tombstoned"),
            rs.getString("metadata"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        ))
        .optional();
  }

  public MemoryRecord createMemory(
      MemoryIdentity identity,
      String requestId,
      String sourceEventId,
      MemoryMutationPlan plan
  ) {
    String memoryId = "mem_" + UUID.randomUUID();
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.memory_records (
              memory_id,
              user_id,
              workspace_id,
              session_id,
              chat_id,
              request_id,
              project_id,
              thread_key,
              scope,
              kind,
              title,
              title_key,
              summary,
              facts,
              source_event_ids,
              version,
              status,
              importance,
              sensitivity,
              consent_level,
              metadata
            ) VALUES (
              :memoryId,
              :userId,
              :workspaceId,
              NULLIF(:sessionId, ''),
              :chatId,
              NULLIF(:requestId, ''),
              :projectId,
              :threadKey,
              :scope,
              :kind,
              :title,
              :titleKey,
              :summary,
              CAST(:facts AS jsonb),
              CAST(:sourceEventIds AS jsonb),
              1,
              'active',
              :importance,
              :sensitivity,
              :consentLevel,
              CAST(:metadata AS jsonb)
            )
            """)
        .param("memoryId", memoryId)
        .param("userId", blank(identity.userId()))
        .param("workspaceId", blank(identity.workspaceId()))
        .param("sessionId", blank(identity.sessionId()))
        .param("chatId", blank(chatId(identity, plan.scope())))
        .param("requestId", blank(requestId))
        .param("projectId", blank(projectId(identity, plan.scope())))
        .param("threadKey", blank(threadKey(identity, plan.scope())))
        .param("scope", plan.scope().name())
        .param("kind", plan.kind().name())
        .param("title", plan.title())
        .param("titleKey", plan.titleKey())
        .param("summary", plan.summary())
        .param("facts", writeJson(plan.facts()))
        .param("sourceEventIds", writeJson(List.of(sourceEventId)))
        .param("importance", plan.importance())
        .param("sensitivity", plan.sensitivity())
        .param("consentLevel", plan.consentLevel())
        .param("metadata", writeJson(plan.metadata()))
        .update();
    return getById(memoryId);
  }

  public MemoryRecord updateMemory(
      String memoryId,
      String requestId,
      List<String> sourceEventIds,
      MemoryMutationPlan plan
  ) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.memory_records
            SET request_id = NULLIF(:requestId, ''),
                title = :title,
                summary = :summary,
                facts = CAST(:facts AS jsonb),
                source_event_ids = CAST(:sourceEventIds AS jsonb),
                version = version + 1,
                importance = :importance,
                sensitivity = :sensitivity,
                consent_level = :consentLevel,
                metadata = CAST(:metadata AS jsonb),
                updated_at = now()
            WHERE memory_id = :memoryId
            """)
        .param("requestId", blank(requestId))
        .param("title", plan.title())
        .param("summary", plan.summary())
        .param("facts", writeJson(plan.facts()))
        .param("sourceEventIds", writeJson(sourceEventIds))
        .param("importance", plan.importance())
        .param("sensitivity", plan.sensitivity())
        .param("consentLevel", plan.consentLevel())
        .param("metadata", writeJson(plan.metadata()))
        .param("memoryId", memoryId)
        .update();
    return getById(memoryId);
  }

  public void supersede(String memoryId, String supersededBy) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.memory_records
            SET status = 'superseded',
                superseded_by = :supersededBy,
                updated_at = now()
            WHERE memory_id = :memoryId
            """)
        .param("memoryId", memoryId)
        .param("supersededBy", supersededBy)
        .update();
  }

  public void tombstone(String memoryId) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.memory_records
            SET status = 'deleted',
                tombstoned = true,
                updated_at = now()
            WHERE memory_id = :memoryId
            """)
        .param("memoryId", memoryId)
        .update();
  }

  public MemoryRecord getById(String memoryId) {
    return jdbcClient.sql("""
            SELECT *
            FROM agent_task_manager.memory_records
            WHERE memory_id = :memoryId
            """)
        .param("memoryId", memoryId)
        .query((rs, rowNum) -> mapRecord(
            rs.getString("memory_id"),
            rs.getString("user_id"),
            rs.getString("workspace_id"),
            rs.getString("session_id"),
            rs.getString("chat_id"),
            rs.getString("request_id"),
            rs.getString("project_id"),
            rs.getString("thread_key"),
            rs.getString("scope"),
            rs.getString("kind"),
            rs.getString("title"),
            rs.getString("title_key"),
            rs.getString("summary"),
            rs.getString("facts"),
            rs.getString("source_event_ids"),
            rs.getInt("version"),
            rs.getString("status"),
            rs.getInt("importance"),
            rs.getString("sensitivity"),
            rs.getString("consent_level"),
            rs.getString("superseded_by"),
            rs.getBoolean("tombstoned"),
            rs.getString("metadata"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        ))
        .single();
  }

  private void lockStableRecordKeys(List<String> keys) {
    keys.stream()
        .distinct()
        .sorted()
        .forEach(key -> jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
            .param("lockKey", key)
            .query((rs, rowNum) -> Boolean.TRUE)
            .single());
  }

  private String stableRecordKey(MemoryIdentity identity, MemoryMutationPlan plan) {
    return stableRecordKey(
        blank(identity.userId()),
        blank(identity.workspaceId()),
        blank(projectId(identity, plan.scope())),
        blank(chatId(identity, plan.scope())),
        blank(threadKey(identity, plan.scope())),
        plan.scope().name(),
        plan.kind().name(),
        plan.titleKey()
    );
  }

  private String stableRecordKey(MemoryRecord record) {
    return stableRecordKey(
        blank(record.userId()),
        blank(record.workspaceId()),
        blank(record.projectId()),
        blank(record.chatId()),
        blank(record.threadKey()),
        record.scope().name(),
        record.kind().name(),
        record.titleKey()
    );
  }

  private String stableRecordKey(
      String userId,
      String workspaceId,
      String projectId,
      String chatId,
      String threadKey,
      String scope,
      String kind,
      String titleKey
  ) {
    return String.join("\u001f", userId, workspaceId, projectId, chatId, threadKey, scope, kind, titleKey);
  }

  private MemoryRecord mapRecord(
      String memoryId,
      String userId,
      String workspaceId,
      String sessionId,
      String chatId,
      String requestId,
      String projectId,
      String threadKey,
      String scope,
      String kind,
      String title,
      String titleKey,
      String summary,
      String facts,
      String sourceEventIds,
      int version,
      String status,
      int importance,
      String sensitivity,
      String consentLevel,
      String supersededBy,
      boolean tombstoned,
      String metadata,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt
  ) {
    return new MemoryRecord(
        memoryId,
        userId,
        workspaceId,
        sessionId,
        chatId,
        requestId,
        projectId,
        threadKey,
        MemoryScope.valueOf(scope),
        MemoryKind.valueOf(kind),
        title,
        titleKey,
        summary,
        readList(facts),
        readList(sourceEventIds),
        version,
        status,
        importance,
        sensitivity,
        consentLevel,
        supersededBy,
        tombstoned,
        readMap(metadata),
        createdAt,
        updatedAt
    );
  }

  private String projectId(MemoryIdentity identity, MemoryScope scope) {
    return scope == MemoryScope.GLOBAL ? "" : blank(identity.projectId());
  }

  private String chatId(MemoryIdentity identity, MemoryScope scope) {
    return scope == MemoryScope.SESSION ? blank(identity.chatId()) : "";
  }

  private String threadKey(MemoryIdentity identity, MemoryScope scope) {
    return scope == MemoryScope.SESSION ? blank(identity.threadKey()) : "";
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to serialize memory record payload.", exception);
    }
  }

  private List<String> readList(String value) {
    try {
      return objectMapper.readValue(value, new TypeReference<>() {
      });
    } catch (Exception exception) {
      return List.of();
    }
  }

  private Map<String, Object> readMap(String value) {
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
