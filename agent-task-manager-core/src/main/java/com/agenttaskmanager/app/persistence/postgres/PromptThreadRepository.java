package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.PromptMessage;
import com.agenttaskmanager.app.model.PromptRequestSummary;
import com.agenttaskmanager.app.model.PromptThreadDetail;
import com.agenttaskmanager.app.model.PromptThreadSummary;
import com.agenttaskmanager.app.model.PromptRequestNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PromptThreadRepository {

  private final JdbcClient jdbcClient;

  public PromptThreadRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
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

  public void recordSession(String threadKey, String threadSessionId) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.prompt_threads
            SET thread_session_id = :threadSessionId,
                last_message_at = now()
            WHERE thread_key = :threadKey
            """)
        .param("threadKey", threadKey)
        .param("threadSessionId", threadSessionId)
        .update();
  }

  public void touchThread(String requestId) {
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

  public List<PromptThreadSummary> list(int limit, String bridgeTarget) {
    return jdbcClient.sql("""
            SELECT
              thread_key,
              project_key,
              repo_path,
              bridge_target,
              thread_session_id,
              last_request_id,
              latest_request_status,
              latest_request_summary,
              latest_prompt_text,
              created_at,
              updated_at,
              last_message_at
            FROM agent_task_manager.prompt_thread_overview
            WHERE (:bridgeTarget = '' OR bridge_target = :bridgeTarget)
            ORDER BY COALESCE(last_message_at, updated_at, created_at) DESC
            LIMIT :limit
            """)
        .param("bridgeTarget", bridgeTarget == null ? "" : bridgeTarget)
        .param("limit", limit)
        .query((rs, rowNum) -> mapThreadSummary(
            rs.getString("thread_key"),
            rs.getString("project_key"),
            rs.getString("repo_path"),
            rs.getString("bridge_target"),
            rs.getString("thread_session_id"),
            rs.getString("last_request_id"),
            rs.getString("latest_request_status"),
            rs.getString("latest_request_summary"),
            rs.getString("latest_prompt_text"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("last_message_at", OffsetDateTime.class)
        ))
        .list();
  }

  public PromptThreadDetail getDetail(String threadKey) {
    return findDetail(threadKey)
        .orElseThrow(() -> new PromptRequestNotFoundException(threadKey));
  }

  public Optional<PromptThreadDetail> findDetail(String threadKey) {
    PromptThreadSummary summary = jdbcClient.sql("""
            SELECT
              thread_key,
              project_key,
              repo_path,
              bridge_target,
              thread_session_id,
              last_request_id,
              latest_request_status,
              latest_request_summary,
              latest_prompt_text,
              created_at,
              updated_at,
              last_message_at
            FROM agent_task_manager.prompt_thread_overview
            WHERE thread_key = :threadKey
            """)
        .param("threadKey", threadKey)
        .query((rs, rowNum) -> mapThreadSummary(
            rs.getString("thread_key"),
            rs.getString("project_key"),
            rs.getString("repo_path"),
            rs.getString("bridge_target"),
            rs.getString("thread_session_id"),
            rs.getString("last_request_id"),
            rs.getString("latest_request_status"),
            rs.getString("latest_request_summary"),
            rs.getString("latest_prompt_text"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("last_message_at", OffsetDateTime.class)
        ))
        .optional()
        .orElse(null);
    if (summary == null) {
      return Optional.empty();
    }

    List<PromptRequestSummary> requests = jdbcClient.sql("""
            SELECT
              request_id,
              project_key,
              repo_path,
              bridge_target,
              thread_key,
              requested_by,
              requested_from,
              target_agent_id,
              execution_mode,
              status,
              prompt_text,
              latest_summary,
              created_at,
              updated_at,
              completed_at,
              latest_run_id,
              latest_run_status,
              latest_run_summary,
              latest_message_at
            FROM agent_task_manager.prompt_request_overview
            WHERE thread_key = :threadKey
            ORDER BY created_at ASC
            """)
        .param("threadKey", threadKey)
        .query(PromptRequestRowMapper::mapSummary)
        .list();

    List<PromptMessage> messages = jdbcClient.sql("""
            SELECT
              message.message_id,
              message.run_id,
              message.message_kind,
              message.sender_name,
              message.body,
              message.created_at
            FROM agent_task_manager.prompt_messages AS message
            JOIN agent_task_manager.prompt_requests AS request
              ON request.request_id = message.request_id
            WHERE request.thread_key = :threadKey
            ORDER BY message.created_at ASC, message.message_id ASC
            LIMIT 400
            """)
        .param("threadKey", threadKey)
        .query((rs, rowNum) -> new PromptMessage(
            rs.getLong("message_id"),
            (Long) rs.getObject("run_id", Long.class),
            rs.getString("message_kind"),
            rs.getString("sender_name"),
            rs.getString("body"),
            rs.getObject("created_at", OffsetDateTime.class)
        ))
        .list();

    return Optional.of(new PromptThreadDetail(summary, requests, messages));
  }

  public List<PromptThreadSummary> search(String queryText, int limit, String bridgeTarget) {
    return jdbcClient.sql("""
            SELECT
              thread_key,
              project_key,
              repo_path,
              bridge_target,
              thread_session_id,
              last_request_id,
              latest_request_status,
              latest_request_summary,
              latest_prompt_text,
              created_at,
              updated_at,
              last_message_at
            FROM agent_task_manager.prompt_thread_overview
            WHERE (
              :queryText = ''
              OR thread_key ILIKE ('%' || :queryText || '%')
              OR latest_prompt_text ILIKE ('%' || :queryText || '%')
              OR latest_request_summary ILIKE ('%' || :queryText || '%')
            )
              AND (:bridgeTarget = '' OR bridge_target = :bridgeTarget)
            ORDER BY COALESCE(last_message_at, updated_at, created_at) DESC
            LIMIT :limit
            """)
        .param("queryText", queryText == null ? "" : queryText.strip())
        .param("bridgeTarget", bridgeTarget == null ? "" : bridgeTarget.strip())
        .param("limit", limit)
        .query((rs, rowNum) -> mapThreadSummary(
            rs.getString("thread_key"),
            rs.getString("project_key"),
            rs.getString("repo_path"),
            rs.getString("bridge_target"),
            rs.getString("thread_session_id"),
            rs.getString("last_request_id"),
            rs.getString("latest_request_status"),
            rs.getString("latest_request_summary"),
            rs.getString("latest_prompt_text"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("last_message_at", OffsetDateTime.class)
        ))
        .list();
  }

  public static String normalizeBridgeTarget(String bridgeTarget) {
    String normalized = bridgeTarget == null ? "" : bridgeTarget.strip().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "", "remote", "remote-headless", "local", "local-ide" -> "remote-headless";
      case "mcp", "mcp-http" -> "mcp-http";
      default -> throw new IllegalArgumentException("Unsupported bridge target: " + bridgeTarget);
    };
  }

  private PromptThreadSummary mapThreadSummary(
      String threadKey,
      String projectKey,
      String repoPath,
      String bridgeTarget,
      String threadSessionId,
      String lastRequestId,
      String latestRequestStatus,
      String latestRequestSummary,
      String latestPromptText,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime lastMessageAt
  ) {
    String preview = latestPromptText;
    if (preview != null) {
      preview = preview.strip();
      if (preview.length() > 140) {
        preview = preview.substring(0, 137) + "...";
      }
    }
    return new PromptThreadSummary(
        threadKey,
        projectKey,
        repoPath,
        bridgeTarget,
        threadSessionId,
        lastRequestId,
        latestRequestStatus,
        latestRequestSummary,
        preview == null ? "" : preview,
        createdAt,
        updatedAt,
        lastMessageAt
    );
  }
}
