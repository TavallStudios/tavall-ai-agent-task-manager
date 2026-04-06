package org.tavall.ai.app.persistence.postgres;

import org.tavall.ai.app.model.orchestration.WorkerLease;
import org.tavall.ai.app.model.orchestration.WorkerTransportKind;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class WorkerLeaseRepository {

  private final JdbcClient jdbcClient;

  public WorkerLeaseRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public void assignWorkerTask(
      String workerTaskId,
      String agentId,
      WorkerTransportKind transportKind,
      String sessionId,
      String leaseToken,
      int leaseDurationSeconds
  ) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.worker_tasks
            SET status = 'ASSIGNED',
                assigned_agent_id = :agentId,
                assigned_transport = :transportKind,
                attempt_count = attempt_count + 1,
                updated_at = now()
            WHERE worker_task_id = :workerTaskId
            """)
        .param("agentId", agentId)
        .param("transportKind", transportKind.name())
        .param("workerTaskId", workerTaskId)
        .update();

    jdbcClient.sql("""
            INSERT INTO agent_task_manager.worker_task_leases (
              worker_task_id,
              task_id,
              agent_id,
              session_id,
              lease_token,
              transport_kind,
              expires_at
            )
            SELECT
              worker_task_id,
              task_id,
              :agentId,
              NULLIF(:sessionId, ''),
              :leaseToken,
              :transportKind,
              now() + make_interval(secs => :leaseDurationSeconds)
            FROM agent_task_manager.worker_tasks
            WHERE worker_task_id = :workerTaskId
            ON CONFLICT (worker_task_id) DO UPDATE SET
              agent_id = EXCLUDED.agent_id,
              session_id = EXCLUDED.session_id,
              lease_token = EXCLUDED.lease_token,
              transport_kind = EXCLUDED.transport_kind,
              heartbeat_at = now(),
              expires_at = EXCLUDED.expires_at
            """)
        .param("agentId", agentId)
        .param("sessionId", sessionId == null ? "" : sessionId)
        .param("leaseToken", leaseToken)
        .param("transportKind", transportKind.name())
        .param("leaseDurationSeconds", leaseDurationSeconds)
        .param("workerTaskId", workerTaskId)
        .update();
  }

  public void heartbeatLease(String workerTaskId, int leaseDurationSeconds) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.worker_task_leases
            SET heartbeat_at = now(),
                expires_at = now() + make_interval(secs => :leaseDurationSeconds)
            WHERE worker_task_id = :workerTaskId
            """)
        .param("leaseDurationSeconds", leaseDurationSeconds)
        .param("workerTaskId", workerTaskId)
        .update();
  }

  public void deleteLease(String workerTaskId) {
    jdbcClient.sql("""
            DELETE FROM agent_task_manager.worker_task_leases
            WHERE worker_task_id = :workerTaskId
            """)
        .param("workerTaskId", workerTaskId)
        .update();
  }

  public List<WorkerLease> findExpiredLeases() {
    return jdbcClient.sql("""
            SELECT
              worker_task_id,
              task_id,
              agent_id,
              session_id,
              lease_token,
              transport_kind,
              acquired_at,
              heartbeat_at,
              expires_at
            FROM agent_task_manager.worker_task_leases
            WHERE expires_at <= now()
            ORDER BY expires_at ASC
            """)
        .query((rs, rowNum) -> new WorkerLease(
            rs.getString("worker_task_id"),
            rs.getString("task_id"),
            rs.getString("agent_id"),
            rs.getString("session_id"),
            rs.getString("lease_token"),
            WorkerTransportKind.valueOf(rs.getString("transport_kind")),
            rs.getObject("acquired_at", OffsetDateTime.class),
            rs.getObject("heartbeat_at", OffsetDateTime.class),
            rs.getObject("expires_at", OffsetDateTime.class)
        ))
        .list();
  }

  public List<WorkerLease> listByTask(String taskId) {
    return jdbcClient.sql("""
            SELECT
              worker_task_id,
              task_id,
              agent_id,
              session_id,
              lease_token,
              transport_kind,
              acquired_at,
              heartbeat_at,
              expires_at
            FROM agent_task_manager.worker_task_leases
            WHERE task_id = :taskId
            ORDER BY expires_at DESC
            """)
        .param("taskId", taskId)
        .query((rs, rowNum) -> new WorkerLease(
            rs.getString("worker_task_id"),
            rs.getString("task_id"),
            rs.getString("agent_id"),
            rs.getString("session_id"),
            rs.getString("lease_token"),
            WorkerTransportKind.valueOf(rs.getString("transport_kind")),
            rs.getObject("acquired_at", OffsetDateTime.class),
            rs.getObject("heartbeat_at", OffsetDateTime.class),
            rs.getObject("expires_at", OffsetDateTime.class)
        ))
        .list();
  }
}

