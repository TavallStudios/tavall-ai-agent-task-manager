package org.tavall.ai.app.persistence.postgres;

import org.tavall.ai.app.model.orchestration.TaskLifecycleStatus;
import org.tavall.ai.app.model.orchestration.WorkerTask;
import org.tavall.ai.app.model.orchestration.WorkerType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class WorkerTaskRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public WorkerTaskRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public WorkerTask createWorkerTask(
      String taskId,
      String parentWorkerTaskId,
      WorkerType workerType,
      String taskRole,
      String title,
      int maxAttempts,
      Map<String, Object> metadata
  ) {
    String workerTaskId = "wt_" + UUID.randomUUID();
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.worker_tasks (
              worker_task_id,
              task_id,
              parent_worker_task_id,
              worker_type,
              task_role,
              title,
              status,
              max_attempts,
              metadata
            ) VALUES (
              :workerTaskId,
              :taskId,
              NULLIF(:parentWorkerTaskId, ''),
              :workerType,
              :taskRole,
              :title,
              'QUEUED',
              :maxAttempts,
              CAST(:metadata AS jsonb)
            )
            """)
        .param("workerTaskId", workerTaskId)
        .param("taskId", taskId)
        .param("parentWorkerTaskId", parentWorkerTaskId == null ? "" : parentWorkerTaskId)
        .param("workerType", workerType.name())
        .param("taskRole", taskRole)
        .param("title", title)
        .param("maxAttempts", maxAttempts)
        .param("metadata", jsonSupport.write(metadata))
        .update();

    return getWorkerTask(workerTaskId);
  }

  public Optional<WorkerTask> claimNextQueuedTask(String taskId) {
    return jdbcClient.sql("""
            WITH next_task AS (
              SELECT worker_task_id
              FROM agent_task_manager.worker_tasks
              WHERE task_id = :taskId
                AND status IN ('QUEUED', 'REASSIGNED', 'NEEDS_REWORK')
              ORDER BY updated_at ASC, created_at ASC
              FOR UPDATE SKIP LOCKED
              LIMIT 1
            )
            UPDATE agent_task_manager.worker_tasks AS worker_task
            SET status = 'ASSIGNED',
                updated_at = now()
            FROM next_task
            WHERE worker_task.worker_task_id = next_task.worker_task_id
            RETURNING
              worker_task.worker_task_id AS worker_task_id,
              worker_task.task_id AS task_id,
              worker_task.parent_worker_task_id AS parent_worker_task_id,
              worker_task.worker_type AS worker_type,
              worker_task.task_role AS task_role,
              worker_task.title AS title,
              worker_task.status AS status,
              worker_task.assigned_agent_id AS assigned_agent_id,
              worker_task.assigned_transport AS assigned_transport,
              worker_task.attempt_count AS attempt_count,
              worker_task.max_attempts AS max_attempts,
              worker_task.latest_summary AS latest_summary,
              worker_task.metadata AS metadata,
              worker_task.created_at AS created_at,
              worker_task.updated_at AS updated_at,
              worker_task.last_check_in_at AS last_check_in_at,
              worker_task.completed_at AS completed_at
            """)
        .param("taskId", taskId)
        .query((rs, rowNum) -> mapWorkerTask(rs))
        .optional();
  }

  public void updateWorkerTaskStatus(String workerTaskId, TaskLifecycleStatus status, String summary) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.worker_tasks
            SET status = :status,
                latest_summary = :summary,
                completed_at = CASE
                  WHEN :status IN ('COMPLETED', 'FAILED', 'DEAD', 'DEAD_LETTER', 'APPROVED')
                  THEN now()
                  ELSE completed_at
                END,
                updated_at = now()
            WHERE worker_task_id = :workerTaskId
            """)
        .param("status", status.name())
        .param("summary", summary)
        .param("workerTaskId", workerTaskId)
        .update();
  }

  public void reassignWorkerTask(String workerTaskId, String summary) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.worker_tasks
            SET status = 'REASSIGNED',
                assigned_agent_id = NULL,
                assigned_transport = NULL,
                latest_summary = :summary,
                updated_at = now()
            WHERE worker_task_id = :workerTaskId
            """)
        .param("summary", summary)
        .param("workerTaskId", workerTaskId)
        .update();
  }

  public List<WorkerTask> listWorkerTasks(String taskId) {
    return jdbcClient.sql("""
            SELECT
              worker_task_id,
              task_id,
              parent_worker_task_id,
              worker_type,
              task_role,
              title,
              status,
              assigned_agent_id,
              assigned_transport,
              attempt_count,
              max_attempts,
              latest_summary,
              metadata,
              created_at,
              updated_at,
              last_check_in_at,
              completed_at
            FROM agent_task_manager.worker_tasks
            WHERE task_id = :taskId
            ORDER BY created_at ASC
            """)
        .param("taskId", taskId)
        .query((rs, rowNum) -> mapWorkerTask(rs))
        .list();
  }

  public WorkerTask getWorkerTask(String workerTaskId) {
    return jdbcClient.sql("""
            SELECT
              worker_task_id,
              task_id,
              parent_worker_task_id,
              worker_type,
              task_role,
              title,
              status,
              assigned_agent_id,
              assigned_transport,
              attempt_count,
              max_attempts,
              latest_summary,
              metadata,
              created_at,
              updated_at,
              last_check_in_at,
              completed_at
            FROM agent_task_manager.worker_tasks
            WHERE worker_task_id = :workerTaskId
            """)
        .param("workerTaskId", workerTaskId)
        .query((rs, rowNum) -> mapWorkerTask(rs))
        .single();
  }

  private WorkerTask mapWorkerTask(java.sql.ResultSet rs) throws java.sql.SQLException {
    String transport = rs.getString("assigned_transport");
    return new WorkerTask(
        rs.getString("worker_task_id"),
        rs.getString("task_id"),
        rs.getString("parent_worker_task_id"),
        WorkerType.valueOf(rs.getString("worker_type")),
        rs.getString("task_role"),
        rs.getString("title"),
        TaskLifecycleStatus.valueOf(rs.getString("status")),
        rs.getString("assigned_agent_id"),
        transport == null ? null : org.tavall.ai.app.model.orchestration.WorkerTransportKind.valueOf(transport),
        rs.getInt("attempt_count"),
        rs.getInt("max_attempts"),
        rs.getString("latest_summary"),
        jsonSupport.readMap(rs.getString("metadata")),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class),
        rs.getObject("last_check_in_at", OffsetDateTime.class),
        rs.getObject("completed_at", OffsetDateTime.class)
    );
  }
}

