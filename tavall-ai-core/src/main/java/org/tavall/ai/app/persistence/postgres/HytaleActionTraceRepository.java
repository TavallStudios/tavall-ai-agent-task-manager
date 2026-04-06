package org.tavall.ai.app.persistence.postgres;

import org.tavall.ai.app.model.hytalelearning.HytaleActionTrace;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class HytaleActionTraceRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public HytaleActionTraceRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public HytaleActionTrace create(
      String traceId,
      String sessionId,
      String commandRequestId,
      String actionKind,
      String commandId,
      String status,
      String summary,
      Map<String, Object> payload
  ) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.hytale_action_traces (
              trace_id,
              session_id,
              command_request_id,
              action_kind,
              command_id,
              status,
              summary,
              payload
            ) VALUES (
              :traceId,
              :sessionId,
              NULLIF(:commandRequestId, ''),
              :actionKind,
              NULLIF(:commandId, ''),
              :status,
              :summary,
              CAST(:payload AS jsonb)
            )
            """)
        .param("traceId", traceId)
        .param("sessionId", sessionId)
        .param("commandRequestId", blank(commandRequestId))
        .param("actionKind", actionKind)
        .param("commandId", blank(commandId))
        .param("status", status)
        .param("summary", summary)
        .param("payload", jsonSupport.write(payload))
        .update();
    return listForSession(sessionId, 1).get(0);
  }

  public List<HytaleActionTrace> listForSession(String sessionId, int limit) {
    return jdbcClient.sql("""
            SELECT
              trace_id,
              session_id,
              command_request_id,
              action_kind,
              command_id,
              status,
              summary,
              payload,
              created_at
            FROM agent_task_manager.hytale_action_traces
            WHERE session_id = :sessionId
            ORDER BY created_at DESC
            LIMIT :limit
            """)
        .param("sessionId", sessionId)
        .param("limit", limit)
        .query((rs, rowNum) -> new HytaleActionTrace(
            rs.getString("trace_id"),
            rs.getString("session_id"),
            rs.getString("command_request_id"),
            rs.getString("action_kind"),
            rs.getString("command_id"),
            rs.getString("status"),
            rs.getString("summary"),
            jsonSupport.readMap(rs.getString("payload")),
            rs.getObject("created_at", OffsetDateTime.class)
        ))
        .list();
  }

  private String blank(String value) {
    return value == null ? "" : value;
  }
}

