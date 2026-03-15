package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.PromptRequestSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public final class PromptRequestRowMapper {

  private PromptRequestRowMapper() {
  }

  public static PromptRequestSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
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
