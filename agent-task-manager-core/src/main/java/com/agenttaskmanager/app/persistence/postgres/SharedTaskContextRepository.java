package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.orchestration.SharedTaskContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SharedTaskContextRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public SharedTaskContextRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public SharedTaskContext storeContext(
      String taskId,
      String workerTaskId,
      String contextKey,
      String visibility,
      String summary,
      Map<String, Object> payload
  ) {
    Optional<String> existingContextId = jdbcClient.sql("""
            SELECT context_id
            FROM agent_task_manager.shared_task_context
            WHERE task_id = :taskId
              AND context_key = :contextKey
              AND COALESCE(worker_task_id, '') = :workerTaskId
            LIMIT 1
            """)
        .param("taskId", taskId)
        .param("contextKey", contextKey)
        .param("workerTaskId", workerTaskId == null ? "" : workerTaskId)
        .query(String.class)
        .optional();

    String contextId = existingContextId.orElseGet(() -> "ctx_" + UUID.randomUUID());
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.shared_task_context (
              context_id,
              task_id,
              worker_task_id,
              context_key,
              visibility,
              summary,
              payload
            ) VALUES (
              :contextId,
              :taskId,
              NULLIF(:workerTaskId, ''),
              :contextKey,
              :visibility,
              :summary,
              CAST(:payload AS jsonb)
            )
            ON CONFLICT (context_id) DO UPDATE SET
              visibility = EXCLUDED.visibility,
              summary = EXCLUDED.summary,
              payload = EXCLUDED.payload,
              updated_at = now()
            """)
        .param("contextId", contextId)
        .param("taskId", taskId)
        .param("workerTaskId", workerTaskId == null ? "" : workerTaskId)
        .param("contextKey", contextKey)
        .param("visibility", visibility)
        .param("summary", summary)
        .param("payload", jsonSupport.write(payload))
        .update();
    return getContext(contextId);
  }

  public SharedTaskContext getContext(String contextId) {
    return jdbcClient.sql("""
            SELECT
              context_id,
              task_id,
              worker_task_id,
              context_key,
              visibility,
              summary,
              payload,
              updated_at
            FROM agent_task_manager.shared_task_context
            WHERE context_id = :contextId
            """)
        .param("contextId", contextId)
        .query((rs, rowNum) -> new SharedTaskContext(
            rs.getString("context_id"),
            rs.getString("task_id"),
            rs.getString("worker_task_id"),
            rs.getString("context_key"),
            rs.getString("visibility"),
            rs.getString("summary"),
            jsonSupport.readMap(rs.getString("payload")),
            rs.getObject("updated_at", OffsetDateTime.class)
        ))
        .single();
  }

  public List<SharedTaskContext> listByTask(String taskId) {
    return jdbcClient.sql("""
            SELECT
              context_id,
              task_id,
              worker_task_id,
              context_key,
              visibility,
              summary,
              payload,
              updated_at
            FROM agent_task_manager.shared_task_context
            WHERE task_id = :taskId
            ORDER BY updated_at DESC
            """)
        .param("taskId", taskId)
        .query((rs, rowNum) -> new SharedTaskContext(
            rs.getString("context_id"),
            rs.getString("task_id"),
            rs.getString("worker_task_id"),
            rs.getString("context_key"),
            rs.getString("visibility"),
            rs.getString("summary"),
            jsonSupport.readMap(rs.getString("payload")),
            rs.getObject("updated_at", OffsetDateTime.class)
        ))
        .list();
  }
}
