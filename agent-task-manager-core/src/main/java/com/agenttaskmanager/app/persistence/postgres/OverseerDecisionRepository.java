package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.orchestration.OverseerDecisionRecord;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OverseerDecisionRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public OverseerDecisionRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public OverseerDecisionRecord storeDecision(
      String taskId,
      String workerTaskId,
      String decisionType,
      TaskLifecycleStatus status,
      String summary,
      Map<String, Object> details
  ) {
    Long decisionId = jdbcClient.sql("""
            INSERT INTO agent_task_manager.overseer_decisions (
              task_id,
              worker_task_id,
              decision_type,
              status,
              summary,
              details
            ) VALUES (
              :taskId,
              NULLIF(:workerTaskId, ''),
              :decisionType,
              :status,
              :summary,
              CAST(:details AS jsonb)
            )
            RETURNING decision_id
            """)
        .param("taskId", taskId)
        .param("workerTaskId", workerTaskId == null ? "" : workerTaskId)
        .param("decisionType", decisionType)
        .param("status", status.name())
        .param("summary", summary)
        .param("details", jsonSupport.write(details))
        .query(Long.class)
        .single();
    return getDecision(decisionId == null ? -1L : decisionId);
  }

  public OverseerDecisionRecord getDecision(long decisionId) {
    return jdbcClient.sql("""
            SELECT
              decision_id,
              task_id,
              worker_task_id,
              decision_type,
              status,
              summary,
              details,
              created_at
            FROM agent_task_manager.overseer_decisions
            WHERE decision_id = :decisionId
            """)
        .param("decisionId", decisionId)
        .query((rs, rowNum) -> new OverseerDecisionRecord(
            rs.getLong("decision_id"),
            rs.getString("task_id"),
            rs.getString("worker_task_id"),
            rs.getString("decision_type"),
            TaskLifecycleStatus.valueOf(rs.getString("status")),
            rs.getString("summary"),
            jsonSupport.readMap(rs.getString("details")),
            rs.getObject("created_at", OffsetDateTime.class)
        ))
        .single();
  }

  public List<OverseerDecisionRecord> listByTask(String taskId) {
    return jdbcClient.sql("""
            SELECT
              decision_id,
              task_id,
              worker_task_id,
              decision_type,
              status,
              summary,
              details,
              created_at
            FROM agent_task_manager.overseer_decisions
            WHERE task_id = :taskId
            ORDER BY created_at DESC
            """)
        .param("taskId", taskId)
        .query((rs, rowNum) -> new OverseerDecisionRecord(
            rs.getLong("decision_id"),
            rs.getString("task_id"),
            rs.getString("worker_task_id"),
            rs.getString("decision_type"),
            TaskLifecycleStatus.valueOf(rs.getString("status")),
            rs.getString("summary"),
            jsonSupport.readMap(rs.getString("details")),
            rs.getObject("created_at", OffsetDateTime.class)
        ))
        .list();
  }
}
