package org.tavall.ai.app.service;

import org.tavall.ai.app.model.TaskCheckpoint;
import org.tavall.ai.app.model.TaskDetail;
import org.tavall.ai.app.model.TaskSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

  private final JdbcClient jdbcClient;

  public TaskService(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public List<TaskSummary> listTasks(String project, String status, int limit) {
    return jdbcClient.sql("""
            SELECT
              task_id,
              project_key,
              source_repo,
              task_kind,
              title,
              status,
              priority,
              owner_agent_id,
              multi_agent_enabled,
              created_at,
              updated_at,
              latest_checkpoint_agent_id,
              latest_checkpoint_status,
              latest_checkpoint_summary,
              latest_checkpoint_at,
              active_lease_agent_id,
              active_lease_session_id,
              active_lease_expires_at
            FROM agent_task_manager.task_overview
            WHERE (:project = '' OR project_key = :project)
              AND (:status = '' OR status = :status)
            ORDER BY updated_at DESC, priority ASC
            LIMIT :limit
            """)
        .param("project", project == null ? "" : project)
        .param("status", status == null ? "" : status)
        .param("limit", limit)
        .query(this::mapTaskSummary)
        .list();
  }

  public TaskDetail getTask(String taskId) {
    TaskSummary task = jdbcClient.sql("""
            SELECT
              task_id,
              project_key,
              source_repo,
              task_kind,
              title,
              status,
              priority,
              owner_agent_id,
              multi_agent_enabled,
              created_at,
              updated_at,
              latest_checkpoint_agent_id,
              latest_checkpoint_status,
              latest_checkpoint_summary,
              latest_checkpoint_at,
              active_lease_agent_id,
              active_lease_session_id,
              active_lease_expires_at
            FROM agent_task_manager.task_overview
            WHERE task_id = :taskId
            """)
        .param("taskId", taskId)
        .query(this::mapTaskSummary)
        .optional()
        .orElseThrow(() -> new TaskNotFoundException(taskId));

    List<TaskCheckpoint> checkpoints = jdbcClient.sql("""
            SELECT checkpoint_id, agent_id, checkpoint_kind, status, summary, created_at
            FROM agent_task_manager.task_checkpoints
            WHERE task_id = :taskId
            ORDER BY created_at DESC
            LIMIT 25
            """)
        .param("taskId", taskId)
        .query((rs, rowNum) -> new TaskCheckpoint(
            rs.getLong("checkpoint_id"),
            rs.getString("agent_id"),
            rs.getString("checkpoint_kind"),
            rs.getString("status"),
            rs.getString("summary"),
            getOffsetDateTime(rs, "created_at")
        ))
        .list();

    return new TaskDetail(task, checkpoints);
  }

  private TaskSummary mapTaskSummary(ResultSet rs, int rowNum) throws SQLException {
    return new TaskSummary(
        rs.getString("task_id"),
        rs.getString("project_key"),
        rs.getString("source_repo"),
        rs.getString("task_kind"),
        rs.getString("title"),
        rs.getString("status"),
        rs.getInt("priority"),
        rs.getString("owner_agent_id"),
        rs.getBoolean("multi_agent_enabled"),
        getOffsetDateTime(rs, "created_at"),
        getOffsetDateTime(rs, "updated_at"),
        rs.getString("latest_checkpoint_agent_id"),
        rs.getString("latest_checkpoint_status"),
        rs.getString("latest_checkpoint_summary"),
        getOffsetDateTime(rs, "latest_checkpoint_at"),
        rs.getString("active_lease_agent_id"),
        rs.getString("active_lease_session_id"),
        getOffsetDateTime(rs, "active_lease_expires_at")
    );
  }

  private static OffsetDateTime getOffsetDateTime(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, OffsetDateTime.class);
  }
}


