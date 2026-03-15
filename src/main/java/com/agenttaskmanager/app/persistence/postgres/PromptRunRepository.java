package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.bridge.BridgeRunHandle;
import com.agenttaskmanager.app.model.PromptRun;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PromptRunRepository {

  private final JdbcClient jdbcClient;
  private final PromptThreadRepository promptThreadRepository;

  public PromptRunRepository(JdbcClient jdbcClient, PromptThreadRepository promptThreadRepository) {
    this.jdbcClient = jdbcClient;
    this.promptThreadRepository = promptThreadRepository;
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

  public void completeRun(String requestId, long runId, String summary, String threadSessionId) {
    updateTerminalRunState("completed", 0, requestId, runId, summary, threadSessionId);
  }

  public void failRun(String requestId, long runId, int exitCode, String summary, String threadSessionId) {
    updateTerminalRunState("failed", exitCode, requestId, runId, summary, threadSessionId);
  }

  public List<PromptRun> listRuns(String requestId) {
    return jdbcClient.sql("""
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
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("completed_at", OffsetDateTime.class)
        ))
        .list();
  }

  private void updateTerminalRunState(
      String status,
      Integer exitCode,
      String requestId,
      long runId,
      String summary,
      String threadSessionId
  ) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.prompt_runs
            SET status = :status,
                exit_code = :exitCode,
                summary = :summary,
                thread_session_id = COALESCE(NULLIF(:threadSessionId, ''), thread_session_id),
                completed_at = now(),
                updated_at = now()
            WHERE run_id = :runId
            """)
        .param("status", status)
        .param("exitCode", exitCode)
        .param("summary", summary)
        .param("threadSessionId", threadSessionId == null ? "" : threadSessionId)
        .param("runId", runId)
        .update();

    jdbcClient.sql("""
            UPDATE agent_task_manager.prompt_requests
            SET status = :status,
                latest_summary = :summary,
                completed_at = now(),
                updated_at = now()
            WHERE request_id = :requestId
            """)
        .param("status", status)
        .param("summary", summary)
        .param("requestId", requestId)
        .update();

    if (threadSessionId != null && !threadSessionId.isBlank()) {
      promptThreadRepository.recordThreadSession(requestId, runId, threadSessionId);
    }
  }
}
