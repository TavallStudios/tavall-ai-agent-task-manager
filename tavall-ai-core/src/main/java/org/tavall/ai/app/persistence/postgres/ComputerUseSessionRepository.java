package org.tavall.ai.app.persistence.postgres;

import org.tavall.ai.app.model.computeruse.ComputerUseSessionRequest;
import org.tavall.ai.app.model.computeruse.ComputerUseSessionSummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ComputerUseSessionRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public ComputerUseSessionRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public ComputerUseSessionSummary createSession(
      ComputerUseSessionRequest request,
      List<String> expectedArtifacts,
      List<String> passFailGates,
      Map<String, Object> artifactPolicy,
      Map<String, Object> metadata
  ) {
    String sessionId = "cu_session_" + UUID.randomUUID();
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.computer_use_sessions (
              session_id,
              runner_id,
              task_id,
              worker_task_id,
              scenario_id,
              server_target,
              chart_id,
              status,
              latest_summary,
              expected_artifacts,
              pass_fail_gates,
              artifact_policy,
              metadata,
              started_at
            ) VALUES (
              :sessionId,
              :runnerId,
              NULLIF(:taskId, ''),
              NULLIF(:workerTaskId, ''),
              :scenarioId,
              :serverTarget,
              :chartId,
              'RUNNING',
              'Session started.',
              CAST(:expectedArtifacts AS jsonb),
              CAST(:passFailGates AS jsonb),
              CAST(:artifactPolicy AS jsonb),
              CAST(:metadata AS jsonb),
              now()
            )
            """)
        .param("sessionId", sessionId)
        .param("runnerId", request.runnerId())
        .param("taskId", emptyIfNull(request.taskId()))
        .param("workerTaskId", emptyIfNull(request.workerTaskId()))
        .param("scenarioId", request.scenarioId())
        .param("serverTarget", request.serverTarget())
        .param("chartId", request.chartId())
        .param("expectedArtifacts", jsonSupport.write(expectedArtifacts))
        .param("passFailGates", jsonSupport.write(passFailGates))
        .param("artifactPolicy", jsonSupport.write(artifactPolicy))
        .param("metadata", jsonSupport.write(metadata))
        .update();
    return getSession(sessionId);
  }

  public ComputerUseSessionSummary getSession(String sessionId) {
    return jdbcClient.sql("""
            SELECT session_id, runner_id, task_id, worker_task_id, scenario_id, server_target,
                   chart_id, status, latest_summary, runner_session_key, expected_artifacts,
                   pass_fail_gates, artifact_policy, metadata, created_at, updated_at,
                   started_at, completed_at
            FROM agent_task_manager.computer_use_sessions
            WHERE session_id = :sessionId
            """)
        .param("sessionId", sessionId)
        .query((rs, rowNum) -> mapSession(rs))
        .single();
  }

  public void updateSessionState(String sessionId, String status, String summary, String runnerSessionKey) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.computer_use_sessions
            SET status = :status,
                latest_summary = :summary,
                runner_session_key = COALESCE(:runnerSessionKey, runner_session_key),
                completed_at = CASE
                  WHEN :status IN ('COMPLETED', 'FAILED', 'STOPPED') THEN COALESCE(completed_at, now())
                  ELSE completed_at
                END,
                updated_at = now()
            WHERE session_id = :sessionId
            """)
        .param("sessionId", sessionId)
        .param("status", status)
        .param("summary", summary)
        .param("runnerSessionKey", runnerSessionKey)
        .update();
  }

  private ComputerUseSessionSummary mapSession(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new ComputerUseSessionSummary(
        rs.getString("session_id"),
        rs.getString("runner_id"),
        rs.getString("task_id"),
        rs.getString("worker_task_id"),
        rs.getString("scenario_id"),
        rs.getString("server_target"),
        rs.getString("chart_id"),
        rs.getString("status"),
        rs.getString("latest_summary"),
        rs.getString("runner_session_key"),
        jsonSupport.readStringList(rs.getString("expected_artifacts")),
        jsonSupport.readStringList(rs.getString("pass_fail_gates")),
        jsonSupport.readMap(rs.getString("artifact_policy")),
        jsonSupport.readMap(rs.getString("metadata")),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class),
        rs.getObject("started_at", OffsetDateTime.class),
        rs.getObject("completed_at", OffsetDateTime.class)
    );
  }

  private static String emptyIfNull(String value) {
    return value == null ? "" : value;
  }
}

