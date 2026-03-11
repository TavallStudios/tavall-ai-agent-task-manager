package com.agenttaskmanager.app.service;

import com.agenttaskmanager.app.bridge.BridgeClaim;
import com.agenttaskmanager.app.bridge.BridgeRunHandle;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class PromptExecutionStore {

  private final JdbcClient jdbcClient;

  public PromptExecutionStore(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
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

  public Optional<BridgeClaim> claimNextQueued(String agentId) {
    return jdbcClient.sql("""
            WITH next_request AS (
              SELECT request_id
              FROM agent_task_manager.prompt_requests
              WHERE status = 'queued'
              ORDER BY created_at ASC
              FOR UPDATE SKIP LOCKED
              LIMIT 1
            )
            UPDATE agent_task_manager.prompt_requests AS request
            SET status = 'claimed',
                target_agent_id = :agentId,
                latest_summary = 'Claimed by Codex bridge',
                updated_at = now()
            FROM next_request
            WHERE request.request_id = next_request.request_id
            RETURNING
              request.request_id,
              request.project_key,
              request.repo_path,
              request.requested_by,
              request.requested_from,
              request.execution_mode,
              request.prompt_text
            """)
        .param("agentId", agentId)
        .query(this::mapBridgeClaim)
        .optional();
  }

  public BridgeRunHandle startRun(String requestId, String sessionId, String agentId) {
    Long runId = jdbcClient.sql("""
            INSERT INTO agent_task_manager.prompt_runs (
              request_id,
              agent_session_id,
              bridge_name,
              status,
              summary
            ) VALUES (
              :requestId,
              :sessionId,
              :bridgeName,
              'running',
              'Codex bridge started'
            )
            RETURNING run_id
            """)
        .param("requestId", requestId)
        .param("sessionId", sessionId)
        .param("bridgeName", agentId)
        .query(Long.class)
        .single();

    jdbcClient.sql("""
            UPDATE agent_task_manager.prompt_requests
            SET status = 'running',
                target_agent_id = :agentId,
                latest_summary = 'Codex bridge is running',
                updated_at = now()
            WHERE request_id = :requestId
            """)
        .param("requestId", requestId)
        .param("agentId", agentId)
        .update();

    return new BridgeRunHandle(runId == null ? -1L : runId, requestId);
  }

  public void appendPromptMessage(
      String requestId,
      long runId,
      String messageKind,
      String senderName,
      String body
  ) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.prompt_messages (
              request_id,
              run_id,
              message_kind,
              sender_name,
              body
            ) VALUES (
              :requestId,
              :runId,
              :messageKind,
              :senderName,
              :body
            )
            """)
        .param("requestId", requestId)
        .param("runId", runId)
        .param("messageKind", messageKind)
        .param("senderName", senderName)
        .param("body", body)
        .update();
  }

  public void completeRun(String requestId, long runId, String summary) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.prompt_runs
            SET status = 'completed',
                exit_code = 0,
                summary = :summary,
                completed_at = now(),
                updated_at = now()
            WHERE run_id = :runId
            """)
        .param("summary", summary)
        .param("runId", runId)
        .update();

    jdbcClient.sql("""
            UPDATE agent_task_manager.prompt_requests
            SET status = 'completed',
                latest_summary = :summary,
                completed_at = now(),
                updated_at = now()
            WHERE request_id = :requestId
            """)
        .param("summary", summary)
        .param("requestId", requestId)
        .update();
  }

  public void failRun(String requestId, long runId, int exitCode, String summary) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.prompt_runs
            SET status = 'failed',
                exit_code = :exitCode,
                summary = :summary,
                completed_at = now(),
                updated_at = now()
            WHERE run_id = :runId
            """)
        .param("exitCode", exitCode)
        .param("summary", summary)
        .param("runId", runId)
        .update();

    jdbcClient.sql("""
            UPDATE agent_task_manager.prompt_requests
            SET status = 'failed',
                latest_summary = :summary,
                completed_at = now(),
                updated_at = now()
            WHERE request_id = :requestId
            """)
        .param("summary", summary)
        .param("requestId", requestId)
        .update();
  }

  private BridgeClaim mapBridgeClaim(ResultSet rs, int rowNum) throws SQLException {
    return new BridgeClaim(
        rs.getString("request_id"),
        rs.getString("project_key"),
        rs.getString("repo_path"),
        rs.getString("requested_by"),
        rs.getString("requested_from"),
        rs.getString("execution_mode"),
        rs.getString("prompt_text")
    );
  }
}
