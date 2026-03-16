package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.orchestration.OverseerTaskBatch;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TaskBatchRepository {

  private final JdbcClient jdbcClient;

  public TaskBatchRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public OverseerTaskBatch createBatch(
      String projectKey,
      String sourceRepo,
      String title,
      String overseerAgentId,
      boolean multiAgentEnabled
  ) {
    String taskId = "tb_" + UUID.randomUUID();
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.agent_tasks (
              task_id,
              project_key,
              source_repo,
              task_kind,
              title,
              status,
              owner_agent_id,
              multi_agent_enabled,
              payload
            ) VALUES (
              :taskId,
              :projectKey,
              :sourceRepo,
              'orchestration-batch',
              :title,
              'CREATED',
              :overseerAgentId,
              :multiAgentEnabled,
              '{}'::jsonb
            )
            """)
        .param("taskId", taskId)
        .param("projectKey", projectKey)
        .param("sourceRepo", sourceRepo)
        .param("title", title)
        .param("overseerAgentId", overseerAgentId)
        .param("multiAgentEnabled", multiAgentEnabled)
        .update();

    return getBatch(taskId);
  }

  public void updateStatus(String taskId, TaskLifecycleStatus status, String ownerAgentId) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.agent_tasks
            SET status = :status,
                owner_agent_id = COALESCE(NULLIF(:ownerAgentId, ''), owner_agent_id),
                updated_at = now()
            WHERE task_id = :taskId
            """)
        .param("status", status.name())
        .param("ownerAgentId", ownerAgentId == null ? "" : ownerAgentId)
        .param("taskId", taskId)
        .update();
  }

  public OverseerTaskBatch getBatch(String taskId) {
    return jdbcClient.sql("""
            SELECT
              task_id,
              project_key,
              source_repo,
              title,
              status,
              owner_agent_id,
              multi_agent_enabled,
              created_at,
              updated_at
            FROM agent_task_manager.agent_tasks
            WHERE task_id = :taskId
            """)
        .param("taskId", taskId)
        .query((rs, rowNum) -> new OverseerTaskBatch(
            rs.getString("task_id"),
            rs.getString("project_key"),
            rs.getString("source_repo"),
            rs.getString("title"),
            TaskLifecycleStatus.valueOf(rs.getString("status")),
            rs.getString("owner_agent_id"),
            rs.getBoolean("multi_agent_enabled"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        ))
        .single();
  }

  public List<OverseerTaskBatch> listBatches(int limit) {
    return jdbcClient.sql("""
            SELECT
              task_id,
              project_key,
              source_repo,
              title,
              status,
              owner_agent_id,
              multi_agent_enabled,
              created_at,
              updated_at
            FROM agent_task_manager.agent_tasks
            WHERE task_kind = 'orchestration-batch'
            ORDER BY updated_at DESC
            LIMIT :limit
            """)
        .param("limit", limit)
        .query((rs, rowNum) -> new OverseerTaskBatch(
            rs.getString("task_id"),
            rs.getString("project_key"),
            rs.getString("source_repo"),
            rs.getString("title"),
            TaskLifecycleStatus.valueOf(rs.getString("status")),
            rs.getString("owner_agent_id"),
            rs.getBoolean("multi_agent_enabled"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        ))
        .list();
  }
}
