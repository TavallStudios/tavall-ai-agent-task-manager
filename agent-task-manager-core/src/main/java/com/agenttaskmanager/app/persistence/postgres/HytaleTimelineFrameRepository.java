package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.hytalelearning.HytaleTimelineFrame;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class HytaleTimelineFrameRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public HytaleTimelineFrameRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public HytaleTimelineFrame create(
      String frameId,
      String sessionId,
      String sourceWindow,
      String artifactKind,
      String storageBackend,
      String storageKey,
      String summary,
      Map<String, Object> metadata
  ) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.hytale_timeline_frames (
              frame_id,
              session_id,
              source_window,
              artifact_kind,
              storage_backend,
              storage_key,
              summary,
              metadata
            ) VALUES (
              :frameId,
              :sessionId,
              :sourceWindow,
              :artifactKind,
              :storageBackend,
              :storageKey,
              :summary,
              CAST(:metadata AS jsonb)
            )
            """)
        .param("frameId", frameId)
        .param("sessionId", sessionId)
        .param("sourceWindow", sourceWindow)
        .param("artifactKind", artifactKind)
        .param("storageBackend", storageBackend)
        .param("storageKey", storageKey)
        .param("summary", summary)
        .param("metadata", jsonSupport.write(metadata))
        .update();
    return listForSession(sessionId, 1).get(0);
  }

  public List<HytaleTimelineFrame> listForSession(String sessionId, int limit) {
    return jdbcClient.sql("""
            SELECT
              frame_id,
              session_id,
              source_window,
              artifact_kind,
              storage_backend,
              storage_key,
              summary,
              metadata,
              created_at
            FROM agent_task_manager.hytale_timeline_frames
            WHERE session_id = :sessionId
            ORDER BY created_at DESC
            LIMIT :limit
            """)
        .param("sessionId", sessionId)
        .param("limit", limit)
        .query((rs, rowNum) -> new HytaleTimelineFrame(
            rs.getString("frame_id"),
            rs.getString("session_id"),
            rs.getString("source_window"),
            rs.getString("artifact_kind"),
            rs.getString("storage_backend"),
            rs.getString("storage_key"),
            rs.getString("summary"),
            jsonSupport.readMap(rs.getString("metadata")),
            rs.getObject("created_at", OffsetDateTime.class)
        ))
        .list();
  }
}
