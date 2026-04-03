package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.computeruse.ComputerUseSessionArtifact;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ComputerUseArtifactRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public ComputerUseArtifactRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public ComputerUseSessionArtifact createArtifact(
      String sessionId,
      String artifactKind,
      String storageKey,
      String summary,
      Map<String, Object> metadata
  ) {
    String artifactId = "cu_artifact_" + UUID.randomUUID();
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.computer_use_session_artifacts (
              artifact_id,
              session_id,
              artifact_kind,
              storage_backend,
              storage_key,
              summary,
              metadata
            ) VALUES (
              :artifactId,
              :sessionId,
              :artifactKind,
              'mongo',
              :storageKey,
              :summary,
              CAST(:metadata AS jsonb)
            )
            """)
        .param("artifactId", artifactId)
        .param("sessionId", sessionId)
        .param("artifactKind", artifactKind)
        .param("storageKey", storageKey)
        .param("summary", summary)
        .param("metadata", jsonSupport.write(metadata))
        .update();
    return getArtifact(artifactId);
  }

  public ComputerUseSessionArtifact getArtifact(String artifactId) {
    return jdbcClient.sql("""
            SELECT artifact_id, session_id, artifact_kind, storage_key, summary, metadata, created_at
            FROM agent_task_manager.computer_use_session_artifacts
            WHERE artifact_id = :artifactId
            """)
        .param("artifactId", artifactId)
        .query((rs, rowNum) -> mapArtifact(rs))
        .single();
  }

  public List<ComputerUseSessionArtifact> listArtifacts(String sessionId) {
    return jdbcClient.sql("""
            SELECT artifact_id, session_id, artifact_kind, storage_key, summary, metadata, created_at
            FROM agent_task_manager.computer_use_session_artifacts
            WHERE session_id = :sessionId
            ORDER BY created_at ASC
            """)
        .param("sessionId", sessionId)
        .query((rs, rowNum) -> mapArtifact(rs))
        .list();
  }

  private ComputerUseSessionArtifact mapArtifact(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new ComputerUseSessionArtifact(
        rs.getString("artifact_id"),
        rs.getString("session_id"),
        rs.getString("artifact_kind"),
        rs.getString("storage_key"),
        rs.getString("summary"),
        jsonSupport.readMap(rs.getString("metadata")),
        rs.getObject("created_at", OffsetDateTime.class)
    );
  }
}
