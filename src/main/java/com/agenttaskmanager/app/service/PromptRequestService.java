package com.agenttaskmanager.app.service;

import com.agenttaskmanager.app.model.PromptMessage;
import com.agenttaskmanager.app.model.PromptRequestDetail;
import com.agenttaskmanager.app.model.PromptRequestFull;
import com.agenttaskmanager.app.model.PromptRequestSummary;
import com.agenttaskmanager.app.model.PromptRun;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class PromptRequestService {

  private static final List<String> EXECUTION_MODES = List.of("read-only", "edit", "run-tests");

  private final JdbcClient jdbcClient;
  private final PromptExecutionStore executionStore;

  public PromptRequestService(JdbcClient jdbcClient, PromptExecutionStore executionStore) {
    this.jdbcClient = jdbcClient;
    this.executionStore = executionStore;
  }

  public PromptRequestSummary create(
      String projectKey,
      String repoPath,
      String bridgeTarget,
      String executionMode,
      String promptText,
      String requestedBy,
      String requestedFrom
  ) {
    String normalizedMode = normalizeExecutionMode(executionMode);
    String normalizedBridgeTarget = PromptExecutionStore.normalizeBridgeTarget(bridgeTarget);
    String requestId = "pr_" + UUID.randomUUID();
    String threadKey = buildThreadKey(repoPath, normalizedBridgeTarget);

    jdbcClient.sql("""
            INSERT INTO agent_task_manager.prompt_requests (
              request_id,
              project_key,
              repo_path,
              bridge_target,
              thread_key,
              requested_by,
              requested_from,
              execution_mode,
              status,
              prompt_text,
              latest_summary
            ) VALUES (
              :requestId,
              :projectKey,
              :repoPath,
              :bridgeTarget,
              :threadKey,
              :requestedBy,
              NULLIF(:requestedFrom, ''),
              :executionMode,
              'queued',
              :promptText,
              'Queued from web control plane'
            )
            """)
        .param("requestId", requestId)
        .param("projectKey", projectKey)
        .param("repoPath", repoPath)
        .param("bridgeTarget", normalizedBridgeTarget)
        .param("threadKey", threadKey)
        .param("requestedBy", requestedBy)
        .param("requestedFrom", requestedFrom == null ? "" : requestedFrom)
        .param("executionMode", normalizedMode)
        .param("promptText", promptText)
        .update();

    executionStore.ensurePromptThread(threadKey, projectKey, repoPath, normalizedBridgeTarget, requestId);

    jdbcClient.sql("""
            INSERT INTO agent_task_manager.prompt_messages (
              request_id,
              message_kind,
              sender_name,
              body
            ) VALUES (
              :requestId,
              'prompt',
              :requestedBy,
              :promptText
            )
            """)
        .param("requestId", requestId)
        .param("requestedBy", requestedBy)
        .param("promptText", promptText)
        .update();

    return getSummary(requestId);
  }

  public List<PromptRequestSummary> list(int limit, String status) {
    return jdbcClient.sql("""
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
            WHERE (:status = '' OR status = :status)
            ORDER BY updated_at DESC, created_at DESC
            LIMIT :limit
            """)
        .param("status", status == null ? "" : status)
        .param("limit", limit)
        .query(this::mapPromptRequestSummary)
        .list();
  }

  public PromptRequestDetail getDetail(String requestId) {
    PromptRequestFull request = jdbcClient.sql("""
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
              completed_at
            FROM agent_task_manager.prompt_requests
            WHERE request_id = :requestId
            """)
        .param("requestId", requestId)
        .query((rs, rowNum) -> new PromptRequestFull(
            rs.getString("request_id"),
            rs.getString("project_key"),
            rs.getString("repo_path"),
            rs.getString("bridge_target"),
            rs.getString("thread_key"),
            rs.getString("requested_by"),
            rs.getString("requested_from"),
            rs.getString("target_agent_id"),
            rs.getString("execution_mode"),
            rs.getString("status"),
            rs.getString("prompt_text"),
            rs.getString("latest_summary"),
            getOffsetDateTime(rs, "created_at"),
            getOffsetDateTime(rs, "updated_at"),
            getOffsetDateTime(rs, "completed_at")
        ))
        .optional()
        .orElseThrow(() -> new PromptRequestNotFoundException(requestId));

    List<PromptRun> runs = jdbcClient.sql("""
            SELECT
              run_id,
              agent_session_id,
              bridge_name,
              thread_session_id,
              status,
              exit_code,
              summary,
              created_at,
              updated_at,
              completed_at
            FROM agent_task_manager.prompt_runs
            WHERE request_id = :requestId
            ORDER BY updated_at DESC, run_id DESC
            LIMIT 20
            """)
        .param("requestId", requestId)
        .query((rs, rowNum) -> new PromptRun(
            rs.getLong("run_id"),
            rs.getString("agent_session_id"),
            rs.getString("bridge_name"),
            rs.getString("thread_session_id"),
            rs.getString("status"),
            (Integer) rs.getObject("exit_code", Integer.class),
            rs.getString("summary"),
            getOffsetDateTime(rs, "created_at"),
            getOffsetDateTime(rs, "updated_at"),
            getOffsetDateTime(rs, "completed_at")
        ))
        .list();

    List<PromptMessage> messages = jdbcClient.sql("""
            SELECT
              message_id,
              run_id,
              message_kind,
              sender_name,
              body,
              created_at
            FROM agent_task_manager.prompt_messages
            WHERE request_id = :requestId
            ORDER BY created_at DESC, message_id DESC
            LIMIT 40
            """)
        .param("requestId", requestId)
        .query((rs, rowNum) -> new PromptMessage(
            rs.getLong("message_id"),
            (Long) rs.getObject("run_id", Long.class),
            rs.getString("message_kind"),
            rs.getString("sender_name"),
            rs.getString("body"),
            getOffsetDateTime(rs, "created_at")
        ))
        .list();

    return new PromptRequestDetail(request, runs, messages);
  }

  public long queuedPromptCount() {
    Long count = jdbcClient.sql("""
            SELECT count(*)
            FROM agent_task_manager.prompt_requests
            WHERE status = 'queued'
            """)
        .query(Long.class)
        .single();
    return count == null ? 0 : count;
  }

  private PromptRequestSummary getSummary(String requestId) {
    return jdbcClient.sql("""
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
            WHERE request_id = :requestId
            """)
        .param("requestId", requestId)
        .query(this::mapPromptRequestSummary)
        .optional()
        .orElseThrow(() -> new PromptRequestNotFoundException(requestId));
  }

  private PromptRequestSummary mapPromptRequestSummary(ResultSet rs, int rowNum) throws SQLException {
    String promptText = rs.getString("prompt_text");
    String preview = promptText == null ? "" : promptText.strip();
    if (preview.length() > 140) {
      preview = preview.substring(0, 137) + "...";
    }
    return new PromptRequestSummary(
        rs.getString("request_id"),
        rs.getString("project_key"),
        rs.getString("repo_path"),
        rs.getString("bridge_target"),
        rs.getString("thread_key"),
        rs.getString("requested_by"),
        rs.getString("requested_from"),
        rs.getString("target_agent_id"),
        rs.getString("execution_mode"),
        rs.getString("status"),
        preview,
        rs.getString("latest_summary"),
        getOffsetDateTime(rs, "created_at"),
        getOffsetDateTime(rs, "updated_at"),
        getOffsetDateTime(rs, "completed_at"),
        (Long) rs.getObject("latest_run_id", Long.class),
        rs.getString("latest_run_status"),
        rs.getString("latest_run_summary"),
        getOffsetDateTime(rs, "latest_message_at")
    );
  }

  private static String normalizeExecutionMode(String executionMode) {
    String normalized = executionMode == null ? "" : executionMode.strip().toLowerCase(Locale.ROOT);
    if (!EXECUTION_MODES.contains(normalized)) {
      throw new IllegalArgumentException("Unsupported execution mode: " + executionMode);
    }
    return normalized;
  }

  private static OffsetDateTime getOffsetDateTime(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, OffsetDateTime.class);
  }

  public static String buildThreadKey(String repoPath, String bridgeTarget) {
    String normalizedPath = repoPath == null ? "" : repoPath.strip().toLowerCase(Locale.ROOT);
    String normalizedTarget = PromptExecutionStore.normalizeBridgeTarget(bridgeTarget);
    return normalizedTarget + ":" + normalizedPath;
  }
}
