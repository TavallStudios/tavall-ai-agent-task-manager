package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.bridge.BridgeClaim;
import com.agenttaskmanager.app.model.PromptMessage;
import com.agenttaskmanager.app.model.PromptRequestDetail;
import com.agenttaskmanager.app.model.PromptRequestFull;
import com.agenttaskmanager.app.model.PromptRequestSummary;
import com.agenttaskmanager.app.model.PromptRequestNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PromptRequestRepository {

  private final JdbcClient jdbcClient;
  private final PromptThreadRepository promptThreadRepository;
  private final PromptMessageRepository promptMessageRepository;
  private final PromptRunRepository promptRunRepository;

  public PromptRequestRepository(
      JdbcClient jdbcClient,
      PromptThreadRepository promptThreadRepository,
      PromptMessageRepository promptMessageRepository,
      PromptRunRepository promptRunRepository
  ) {
    this.jdbcClient = jdbcClient;
    this.promptThreadRepository = promptThreadRepository;
    this.promptMessageRepository = promptMessageRepository;
    this.promptRunRepository = promptRunRepository;
  }

  public PromptRequestSummary create(
      String projectKey,
      String repoPath,
      String bridgeTarget,
      String threadKeyOverride,
      String executionMode,
      String promptText,
      String requestedBy,
      String requestedFrom
  ) {
    String requestId = "pr_" + UUID.randomUUID();
    String normalizedBridgeTarget = PromptThreadRepository.normalizeBridgeTarget(bridgeTarget);
    String threadKey = threadKeyOverride == null || threadKeyOverride.isBlank()
        ? buildThreadKey(repoPath, normalizedBridgeTarget)
        : threadKeyOverride.strip();

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
        .param("executionMode", executionMode)
        .param("promptText", promptText)
        .update();

    promptThreadRepository.ensurePromptThread(threadKey, projectKey, repoPath, normalizedBridgeTarget, requestId);
    promptMessageRepository.appendPromptMessage(requestId, null, "prompt", requestedBy, promptText);
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
        .query(PromptRequestRowMapper::mapSummary)
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
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("completed_at", OffsetDateTime.class)
        ))
        .optional()
        .orElseThrow(() -> new PromptRequestNotFoundException(requestId));

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
            rs.getObject("created_at", OffsetDateTime.class)
        ))
        .list();

    return new PromptRequestDetail(request, promptRunRepository.listRuns(requestId), messages);
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
        .query((rs, rowNum) -> new BridgeClaim(
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
        ))
        .optional();
  }

  public PromptRequestSummary getSummary(String requestId) {
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
        .query(PromptRequestRowMapper::mapSummary)
        .optional()
        .orElseThrow(() -> new PromptRequestNotFoundException(requestId));
  }

  public static String buildThreadKey(String repoPath, String bridgeTarget) {
    String normalizedPath = repoPath == null ? "" : repoPath.strip().toLowerCase();
    String normalizedTarget = PromptThreadRepository.normalizeBridgeTarget(bridgeTarget);
    return normalizedTarget + ":" + normalizedPath;
  }
}
