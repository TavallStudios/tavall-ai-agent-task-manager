package com.agenttaskmanager.app.service;

import com.agenttaskmanager.app.bridge.BridgeClaim;
import com.agenttaskmanager.app.bridge.BridgeRunHandle;
import com.agenttaskmanager.app.model.BridgeSessionSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
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

  public Optional<BridgeClaim> claimNextQueued(String agentId, String bridgeTarget) {
    return jdbcClient.sql("""
            WITH next_request AS (
              SELECT request_id
              FROM agent_task_manager.prompt_requests
              WHERE status = 'queued'
                AND bridge_target = :bridgeTarget
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
              request.bridge_target,
              request.thread_key,
              request.requested_by,
              request.requested_from,
              request.execution_mode,
              request.prompt_text,
              (
                SELECT thread.thread_session_id
                FROM agent_task_manager.prompt_threads AS thread
                WHERE thread.thread_key = request.thread_key
              ) AS resume_session_id
            """)
        .param("agentId", agentId)
        .param("bridgeTarget", bridgeTarget)
        .query(this::mapBridgeClaim)
        .optional();
  }

  public BridgeRunHandle startRun(String requestId, String sessionId, String agentId) {
    return startRun(requestId, sessionId, agentId, null);
  }

  public BridgeRunHandle startRun(
      String requestId,
      String sessionId,
      String agentId,
      String threadKey
  ) {
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

    return new BridgeRunHandle(runId == null ? -1L : runId, requestId, threadKey);
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

    jdbcClient.sql("""
            UPDATE agent_task_manager.prompt_threads
            SET last_request_id = :requestId,
                last_message_at = now()
            WHERE thread_key = (
              SELECT thread_key
              FROM agent_task_manager.prompt_requests
              WHERE request_id = :requestId
            )
            """)
        .param("requestId", requestId)
        .update();
  }

  public void completeRun(String requestId, long runId, String summary) {
    completeRun(requestId, runId, summary, null);
  }

  public void completeRun(String requestId, long runId, String summary, String threadSessionId) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.prompt_runs
            SET status = 'completed',
                exit_code = 0,
                summary = :summary,
                thread_session_id = COALESCE(NULLIF(:threadSessionId, ''), thread_session_id),
                completed_at = now(),
                updated_at = now()
            WHERE run_id = :runId
            """)
        .param("summary", summary)
        .param("threadSessionId", threadSessionId == null ? "" : threadSessionId)
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

    if (threadSessionId != null && !threadSessionId.isBlank()) {
      recordThreadSession(requestId, runId, threadSessionId);
    }
  }

  public void failRun(String requestId, long runId, int exitCode, String summary) {
    failRun(requestId, runId, exitCode, summary, null);
  }

  public void failRun(
      String requestId,
      long runId,
      int exitCode,
      String summary,
      String threadSessionId
  ) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.prompt_runs
            SET status = 'failed',
                exit_code = :exitCode,
                summary = :summary,
                thread_session_id = COALESCE(NULLIF(:threadSessionId, ''), thread_session_id),
                completed_at = now(),
                updated_at = now()
            WHERE run_id = :runId
            """)
        .param("exitCode", exitCode)
        .param("summary", summary)
        .param("threadSessionId", threadSessionId == null ? "" : threadSessionId)
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

    if (threadSessionId != null && !threadSessionId.isBlank()) {
      recordThreadSession(requestId, runId, threadSessionId);
    }
  }

  public void recordThreadSession(String requestId, long runId, String threadSessionId) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.prompt_runs
            SET thread_session_id = :threadSessionId,
                updated_at = now()
            WHERE run_id = :runId
            """)
        .param("threadSessionId", threadSessionId)
        .param("runId", runId)
        .update();

    jdbcClient.sql("""
            INSERT INTO agent_task_manager.prompt_threads (
              thread_key,
              project_key,
              repo_path,
              bridge_target,
              thread_session_id,
              last_request_id,
              last_message_at
            )
            SELECT
              request.thread_key,
              request.project_key,
              request.repo_path,
              request.bridge_target,
              :threadSessionId,
              request.request_id,
              now()
            FROM agent_task_manager.prompt_requests AS request
            WHERE request.request_id = :requestId
            ON CONFLICT (thread_key) DO UPDATE SET
              thread_session_id = EXCLUDED.thread_session_id,
              last_request_id = EXCLUDED.last_request_id,
              last_message_at = now()
            """)
        .param("threadSessionId", threadSessionId)
        .param("requestId", requestId)
        .update();
  }

  public void ensurePromptThread(
      String threadKey,
      String projectKey,
      String repoPath,
      String bridgeTarget,
      String requestId
  ) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.prompt_threads (
              thread_key,
              project_key,
              repo_path,
              bridge_target,
              last_request_id,
              last_message_at
            ) VALUES (
              :threadKey,
              :projectKey,
              :repoPath,
              :bridgeTarget,
              :requestId,
              now()
            )
            ON CONFLICT (thread_key) DO UPDATE SET
              last_request_id = EXCLUDED.last_request_id,
              last_message_at = COALESCE(agent_task_manager.prompt_threads.last_message_at, now())
            """)
        .param("threadKey", threadKey)
        .param("projectKey", projectKey)
        .param("repoPath", repoPath)
        .param("bridgeTarget", normalizeBridgeTarget(bridgeTarget))
        .param("requestId", requestId)
        .update();
  }

  public static String normalizeBridgeTarget(String bridgeTarget) {
    String normalized = bridgeTarget == null ? "" : bridgeTarget.strip().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "", "remote", "remote-headless" -> "remote-headless";
      case "local", "local-ide" -> "local-ide";
      default -> throw new IllegalArgumentException("Unsupported bridge target: " + bridgeTarget);
    };
  }

  private BridgeClaim mapBridgeClaim(ResultSet rs, int rowNum) throws SQLException {
    return new BridgeClaim(
        rs.getString("request_id"),
        rs.getString("project_key"),
        rs.getString("repo_path"),
        rs.getString("bridge_target"),
        rs.getString("thread_key"),
        rs.getString("resume_session_id"),
        rs.getString("requested_by"),
        rs.getString("requested_from"),
        rs.getString("execution_mode"),
        rs.getString("prompt_text")
    );
  }
}
