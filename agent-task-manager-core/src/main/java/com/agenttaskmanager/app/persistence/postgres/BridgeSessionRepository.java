package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.BridgeSessionSummary;
import com.agenttaskmanager.app.model.bridge.BridgeSessionRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BridgeSessionRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public BridgeSessionRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public void upsertAgentSession(
      String sessionId,
      String agentId,
      String status,
      String hostName,
      String clientName
  ) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.agent_sessions (
              session_id,
              agent_id,
              host_name,
              client_name,
              status,
              last_seen_at
            ) VALUES (
              :sessionId,
              :agentId,
              :hostName,
              :clientName,
              :status,
              now()
            )
            ON CONFLICT (session_id) DO UPDATE SET
              agent_id = EXCLUDED.agent_id,
              host_name = EXCLUDED.host_name,
              client_name = EXCLUDED.client_name,
              status = EXCLUDED.status,
              last_seen_at = now()
            """)
        .param("sessionId", sessionId)
        .param("agentId", agentId)
        .param("hostName", hostName)
        .param("clientName", clientName)
        .param("status", status)
        .update();
  }

  public void upsertBridgeSession(
      String sessionId,
      String agentId,
      String status,
      String hostName,
      String clientName,
      String repoPath,
      String capabilitiesJson
  ) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.agent_sessions (
              session_id,
              agent_id,
              host_name,
              client_name,
              repo_path,
              status,
              capabilities,
              last_seen_at
            ) VALUES (
              :sessionId,
              :agentId,
              :hostName,
              :clientName,
              NULLIF(:repoPath, ''),
              :status,
              CAST(:capabilities AS jsonb),
              now()
            )
            ON CONFLICT (session_id) DO UPDATE SET
              agent_id = EXCLUDED.agent_id,
              host_name = EXCLUDED.host_name,
              client_name = EXCLUDED.client_name,
              repo_path = EXCLUDED.repo_path,
              status = EXCLUDED.status,
              capabilities = EXCLUDED.capabilities,
              last_seen_at = now()
            """)
        .param("sessionId", sessionId)
        .param("agentId", agentId)
        .param("hostName", hostName)
        .param("clientName", clientName)
        .param("repoPath", repoPath == null ? "" : repoPath)
        .param("status", status)
        .param("capabilities", capabilitiesJson == null || capabilitiesJson.isBlank() ? "{}" : capabilitiesJson)
        .update();
  }

  public void heartbeatAgentSession(String sessionId, String status) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.agent_sessions
            SET status = :status,
                last_seen_at = now()
            WHERE session_id = :sessionId
            """)
        .param("status", status)
        .param("sessionId", sessionId)
        .update();
  }

  public List<BridgeSessionSummary> listBridgeSessions(int limit) {
    return jdbcClient.sql("""
            SELECT
              session_id,
              agent_id,
              client_name,
              host_name,
              repo_path,
              COALESCE(capabilities ->> 'bridgeTarget', '') AS bridge_target,
              COALESCE(capabilities ->> 'transport', '') AS transport,
              status,
              last_seen_at,
              (
                status NOT IN ('offline', 'disabled')
                AND last_seen_at > now() - interval '90 seconds'
              ) AS online
            FROM agent_task_manager.agent_sessions
            WHERE COALESCE(capabilities ->> 'bridgeTarget', '') <> ''
            ORDER BY last_seen_at DESC, updated_at DESC
            LIMIT :limit
            """)
        .param("limit", limit)
        .query((rs, rowNum) -> new BridgeSessionSummary(
            rs.getString("session_id"),
            rs.getString("agent_id"),
            rs.getString("client_name"),
            rs.getString("host_name"),
            rs.getString("repo_path"),
            rs.getString("bridge_target"),
            rs.getString("transport"),
            rs.getString("status"),
            rs.getBoolean("online"),
            rs.getObject("last_seen_at", OffsetDateTime.class)
        ))
        .list();
  }

  public Optional<BridgeSessionRecord> findBridgeSession(String sessionId) {
    return jdbcClient.sql("""
            SELECT
              session_id,
              agent_id,
              client_name,
              host_name,
              repo_path,
              COALESCE(capabilities ->> 'bridgeTarget', '') AS bridge_target,
              COALESCE(capabilities ->> 'transport', '') AS transport,
              status,
              (
                status NOT IN ('offline', 'disabled')
                AND last_seen_at > now() - interval '90 seconds'
              ) AS online,
              last_seen_at,
              capabilities
            FROM agent_task_manager.agent_sessions
            WHERE session_id = :sessionId
            """)
        .param("sessionId", sessionId)
        .query((rs, rowNum) -> new BridgeSessionRecord(
            rs.getString("session_id"),
            rs.getString("agent_id"),
            rs.getString("client_name"),
            rs.getString("host_name"),
            rs.getString("repo_path"),
            rs.getString("bridge_target"),
            rs.getString("transport"),
            rs.getString("status"),
            rs.getBoolean("online"),
            rs.getObject("last_seen_at", OffsetDateTime.class),
            jsonSupport.readMap(rs.getString("capabilities"))
        ))
        .optional();
  }
}
