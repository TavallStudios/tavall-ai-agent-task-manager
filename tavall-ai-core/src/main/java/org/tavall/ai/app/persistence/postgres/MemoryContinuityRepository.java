package org.tavall.ai.app.persistence.postgres;

import org.tavall.ai.app.memory.MemoryContinuitySnapshot;
import org.tavall.ai.app.memory.MemoryIdentity;
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
public class MemoryContinuityRepository {

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  public MemoryContinuityRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
  }

  public MemoryContinuitySnapshot upsert(
      MemoryIdentity identity,
      String requestId,
      String summary,
      List<Map<String, Object>> workingMemory,
      List<String> memoryIds,
      Map<String, Object> sourceCounts,
      Map<String, Object> metadata
  ) {
    String snapshotId = "mcs_" + UUID.randomUUID();
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.memory_continuity_snapshots (
              continuity_snapshot_id,
              user_id,
              workspace_id,
              project_id,
              chat_id,
              thread_key,
              session_id,
              api_key_id,
              request_id,
              summary,
              working_memory,
              memory_ids,
              source_counts,
              metadata
            ) VALUES (
              :snapshotId,
              :userId,
              :workspaceId,
              :projectId,
              :chatId,
              :threadKey,
              NULLIF(:sessionId, ''),
              NULLIF(:apiKeyId, ''),
              NULLIF(:requestId, ''),
              :summary,
              CAST(:workingMemory AS jsonb),
              CAST(:memoryIds AS jsonb),
              CAST(:sourceCounts AS jsonb),
              CAST(:metadata AS jsonb)
            )
            ON CONFLICT (user_id, workspace_id, project_id, chat_id, thread_key) DO UPDATE SET
              session_id = EXCLUDED.session_id,
              api_key_id = EXCLUDED.api_key_id,
              request_id = EXCLUDED.request_id,
              summary = EXCLUDED.summary,
              working_memory = EXCLUDED.working_memory,
              memory_ids = EXCLUDED.memory_ids,
              source_counts = EXCLUDED.source_counts,
              metadata = EXCLUDED.metadata,
              updated_at = now()
            """)
        .param("snapshotId", snapshotId)
        .param("userId", blank(identity.userId()))
        .param("workspaceId", blank(identity.workspaceId()))
        .param("projectId", blank(identity.projectId()))
        .param("chatId", blank(identity.chatId()))
        .param("threadKey", blank(identity.threadKey()))
        .param("sessionId", blank(identity.sessionId()))
        .param("apiKeyId", blank(identity.apiKeyId()))
        .param("requestId", blank(requestId))
        .param("summary", blank(summary))
        .param("workingMemory", writeJson(workingMemory))
        .param("memoryIds", writeJson(memoryIds))
        .param("sourceCounts", writeJson(sourceCounts))
        .param("metadata", writeJson(metadata))
        .update();
    return find(identity).orElseThrow();
  }

  public Optional<MemoryContinuitySnapshot> find(MemoryIdentity identity) {
    return jdbcClient.sql("""
            SELECT *
            FROM agent_task_manager.memory_continuity_snapshots
            WHERE user_id = :userId
              AND workspace_id = :workspaceId
              AND project_id = :projectId
              AND chat_id = :chatId
              AND thread_key = :threadKey
            ORDER BY updated_at DESC
            LIMIT 1
            """)
        .param("userId", blank(identity.userId()))
        .param("workspaceId", blank(identity.workspaceId()))
        .param("projectId", blank(identity.projectId()))
        .param("chatId", blank(identity.chatId()))
        .param("threadKey", blank(identity.threadKey()))
        .query((rs, rowNum) -> new MemoryContinuitySnapshot(
            rs.getString("continuity_snapshot_id"),
            rs.getString("user_id"),
            rs.getString("workspace_id"),
            rs.getString("project_id"),
            rs.getString("chat_id"),
            rs.getString("thread_key"),
            rs.getString("session_id"),
            rs.getString("api_key_id"),
            rs.getString("request_id"),
            rs.getString("summary"),
            readListOfMaps(rs.getString("working_memory")),
            readList(rs.getString("memory_ids")),
            readMap(rs.getString("source_counts")),
            readMap(rs.getString("metadata")),
            rs.getObject("updated_at", OffsetDateTime.class)
        ))
        .optional();
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to serialize continuity payload.", exception);
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

  private List<Map<String, Object>> readListOfMaps(String value) {
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

