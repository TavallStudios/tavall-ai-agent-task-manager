package com.agenttaskmanager.app.service;

import com.agenttaskmanager.app.model.PromptMessage;
import com.agenttaskmanager.app.model.PromptRequestSummary;
import com.agenttaskmanager.app.model.PromptThreadDetail;
import com.agenttaskmanager.app.model.PromptThreadSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class PromptThreadService {

  private final JdbcClient jdbcClient;

  public PromptThreadService(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
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
        .query(this::mapThreadSummary)
        .list();
  }

  public PromptThreadDetail getDetail(String threadKey) {
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
        .query(this::mapThreadSummary)
        .optional()
        .orElseThrow(() -> new PromptRequestNotFoundException(threadKey));

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
        .query(this::mapRequestSummary)
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

    return new PromptThreadDetail(summary, requests, messages);
  }

  private PromptThreadSummary mapThreadSummary(ResultSet rs, int rowNum) throws SQLException {
    String preview = rs.getString("latest_prompt_text");
    if (preview != null) {
      preview = preview.strip();
      if (preview.length() > 140) {
        preview = preview.substring(0, 137) + "...";
      }
    }
    return new PromptThreadSummary(
        rs.getString("thread_key"),
        rs.getString("project_key"),
        rs.getString("repo_path"),
        rs.getString("bridge_target"),
        rs.getString("thread_session_id"),
        rs.getString("last_request_id"),
        rs.getString("latest_request_status"),
        rs.getString("latest_request_summary"),
        preview == null ? "" : preview,
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class),
        rs.getObject("last_message_at", OffsetDateTime.class)
    );
  }

  private PromptRequestSummary mapRequestSummary(ResultSet rs, int rowNum) throws SQLException {
    String preview = rs.getString("prompt_text");
    if (preview != null) {
      preview = preview.strip();
      if (preview.length() > 140) {
        preview = preview.substring(0, 137) + "...";
      }
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
        preview == null ? "" : preview,
        rs.getString("latest_summary"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class),
        rs.getObject("completed_at", OffsetDateTime.class),
        (Long) rs.getObject("latest_run_id", Long.class),
        rs.getString("latest_run_status"),
        rs.getString("latest_run_summary"),
        rs.getObject("latest_message_at", OffsetDateTime.class)
    );
  }
}
