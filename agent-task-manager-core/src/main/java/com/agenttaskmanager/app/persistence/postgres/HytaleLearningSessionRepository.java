package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.HytaleLearningNotFoundException;
import com.agenttaskmanager.app.model.hytalelearning.HytaleLearningSession;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class HytaleLearningSessionRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public HytaleLearningSessionRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public HytaleLearningSession create(
      String sessionId,
      String bridgeSessionId,
      String machineId,
      String clientProfileId,
      String clientInstallPath,
      String serverTarget,
      String scenarioId,
      Map<String, Object> metadata
  ) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.hytale_learning_sessions (
              session_id,
              bridge_session_id,
              machine_id,
              client_profile_id,
              client_install_path,
              server_target,
              scenario_id,
              status,
              latest_summary,
              metadata
            ) VALUES (
              :sessionId,
              NULLIF(:bridgeSessionId, ''),
              :machineId,
              NULLIF(:clientProfileId, ''),
              NULLIF(:clientInstallPath, ''),
              NULLIF(:serverTarget, ''),
              NULLIF(:scenarioId, ''),
              'active',
              'Learning session started',
              CAST(:metadata AS jsonb)
            )
            """)
        .param("sessionId", sessionId)
        .param("bridgeSessionId", blank(bridgeSessionId))
        .param("machineId", machineId)
        .param("clientProfileId", blank(clientProfileId))
        .param("clientInstallPath", blank(clientInstallPath))
        .param("serverTarget", blank(serverTarget))
        .param("scenarioId", blank(scenarioId))
        .param("metadata", jsonSupport.write(metadata))
        .update();
    return get(sessionId);
  }

  public HytaleLearningSession updateStatus(
      String sessionId,
      String status,
      String latestSummary,
      Map<String, Object> metadata,
      boolean completed
  ) {
    int updated = jdbcClient.sql("""
            UPDATE agent_task_manager.hytale_learning_sessions
            SET status = :status,
                latest_summary = :latestSummary,
                metadata = CAST(:metadata AS jsonb),
                completed_at = CASE WHEN :completed THEN now() ELSE completed_at END,
                updated_at = now()
            WHERE session_id = :sessionId
            """)
        .param("status", status)
        .param("latestSummary", latestSummary)
        .param("metadata", jsonSupport.write(metadata))
        .param("completed", completed)
        .param("sessionId", sessionId)
        .update();
    if (updated == 0) {
      throw new HytaleLearningNotFoundException("Hytale learning session not found: " + sessionId);
    }
    return get(sessionId);
  }

  public HytaleLearningSession get(String sessionId) {
    return find(sessionId)
        .orElseThrow(() -> new HytaleLearningNotFoundException("Hytale learning session not found: " + sessionId));
  }

  public Optional<HytaleLearningSession> find(String sessionId) {
    return jdbcClient.sql("""
            SELECT
              session_id,
              bridge_session_id,
              machine_id,
              client_profile_id,
              client_install_path,
              server_target,
              scenario_id,
              status,
              latest_summary,
              metadata,
              created_at,
              updated_at,
              completed_at
            FROM agent_task_manager.hytale_learning_sessions
            WHERE session_id = :sessionId
            """)
        .param("sessionId", sessionId)
        .query((rs, rowNum) -> mapSession(
            rs.getString("session_id"),
            rs.getString("bridge_session_id"),
            rs.getString("machine_id"),
            rs.getString("client_profile_id"),
            rs.getString("client_install_path"),
            rs.getString("server_target"),
            rs.getString("scenario_id"),
            rs.getString("status"),
            rs.getString("latest_summary"),
            rs.getString("metadata"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("completed_at", OffsetDateTime.class)
        ))
        .optional();
  }

  public List<HytaleLearningSession> listByScope(
      String machineId,
      String clientProfileId,
      String serverTarget,
      String scenarioId,
      int limit
  ) {
    return jdbcClient.sql("""
            SELECT
              session_id,
              bridge_session_id,
              machine_id,
              client_profile_id,
              client_install_path,
              server_target,
              scenario_id,
              status,
              latest_summary,
              metadata,
              created_at,
              updated_at,
              completed_at
            FROM agent_task_manager.hytale_learning_sessions
            WHERE (:machineId = '' OR machine_id = :machineId)
              AND (:clientProfileId = '' OR COALESCE(client_profile_id, '') = :clientProfileId)
              AND (:serverTarget = '' OR COALESCE(server_target, '') = :serverTarget)
              AND (:scenarioId = '' OR COALESCE(scenario_id, '') = :scenarioId)
            ORDER BY updated_at DESC, created_at DESC
            LIMIT :limit
            """)
        .param("machineId", blank(machineId))
        .param("clientProfileId", blank(clientProfileId))
        .param("serverTarget", blank(serverTarget))
        .param("scenarioId", blank(scenarioId))
        .param("limit", limit)
        .query((rs, rowNum) -> mapSession(
            rs.getString("session_id"),
            rs.getString("bridge_session_id"),
            rs.getString("machine_id"),
            rs.getString("client_profile_id"),
            rs.getString("client_install_path"),
            rs.getString("server_target"),
            rs.getString("scenario_id"),
            rs.getString("status"),
            rs.getString("latest_summary"),
            rs.getString("metadata"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("completed_at", OffsetDateTime.class)
        ))
        .list();
  }

  private HytaleLearningSession mapSession(
      String sessionId,
      String bridgeSessionId,
      String machineId,
      String clientProfileId,
      String clientInstallPath,
      String serverTarget,
      String scenarioId,
      String status,
      String latestSummary,
      String metadataJson,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime completedAt
  ) {
    return new HytaleLearningSession(
        sessionId,
        bridgeSessionId,
        machineId,
        clientProfileId,
        clientInstallPath,
        serverTarget,
        scenarioId,
        status,
        latestSummary,
        jsonSupport.readMap(metadataJson),
        createdAt,
        updatedAt,
        completedAt
    );
  }

  private String blank(String value) {
    return value == null ? "" : value;
  }
}
