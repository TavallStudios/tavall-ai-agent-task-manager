package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import com.agenttaskmanager.app.model.orchestration.WorkerCheckIn;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class WorkerCheckInRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public WorkerCheckInRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public WorkerCheckIn appendCheckIn(
      String workerTaskId,
      String taskId,
      String agentId,
      TaskLifecycleStatus status,
      String summary,
      Map<String, Object> details
  ) {
    Long checkInId = jdbcClient.sql("""
            INSERT INTO agent_task_manager.worker_checkins (
              worker_task_id,
              task_id,
              agent_id,
              status,
              summary,
              details
            ) VALUES (
              :workerTaskId,
              :taskId,
              :agentId,
              :status,
              :summary,
              CAST(:details AS jsonb)
            )
            RETURNING check_in_id
            """)
        .param("workerTaskId", workerTaskId)
        .param("taskId", taskId)
        .param("agentId", agentId)
        .param("status", status.name())
        .param("summary", summary)
        .param("details", jsonSupport.write(details))
        .query(Long.class)
        .single();

    jdbcClient.sql("""
            UPDATE agent_task_manager.worker_tasks
            SET status = :status,
                latest_summary = :summary,
                last_check_in_at = now(),
                updated_at = now()
            WHERE worker_task_id = :workerTaskId
            """)
        .param("status", status.name())
        .param("summary", summary)
        .param("workerTaskId", workerTaskId)
        .update();

    return new WorkerCheckIn(
        checkInId == null ? -1L : checkInId,
        workerTaskId,
        taskId,
        agentId,
        status,
        summary,
        details,
        OffsetDateTime.now()
    );
  }

  public List<WorkerCheckIn> listCheckIns(String workerTaskId) {
    return jdbcClient.sql("""
            SELECT
              check_in_id,
              worker_task_id,
              task_id,
              agent_id,
              status,
              summary,
              details,
              created_at
            FROM agent_task_manager.worker_checkins
            WHERE worker_task_id = :workerTaskId
            ORDER BY created_at DESC
            """)
        .param("workerTaskId", workerTaskId)
        .query((rs, rowNum) -> new WorkerCheckIn(
            rs.getLong("check_in_id"),
            rs.getString("worker_task_id"),
            rs.getString("task_id"),
            rs.getString("agent_id"),
            TaskLifecycleStatus.valueOf(rs.getString("status")),
            rs.getString("summary"),
            jsonSupport.readMap(rs.getString("details")),
            rs.getObject("created_at", OffsetDateTime.class)
        ))
        .list();
  }
}
