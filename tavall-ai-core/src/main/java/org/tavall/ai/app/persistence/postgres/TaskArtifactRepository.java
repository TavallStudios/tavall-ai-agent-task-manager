package org.tavall.ai.app.persistence.postgres;

import org.tavall.ai.app.model.orchestration.ArtifactRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TaskArtifactRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public TaskArtifactRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public ArtifactRecord storeArtifact(
      String taskId,
      String workerTaskId,
      String artifactKind,
      String storageBackend,
      String storageKey,
      String summary,
      Map<String, Object> metadata
  ) {
    String artifactId = "artifact_" + UUID.randomUUID();
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.task_artifacts (
              artifact_id,
              task_id,
              worker_task_id,
              artifact_kind,
              storage_backend,
              storage_key,
              summary,
              metadata
            ) VALUES (
              :artifactId,
              :taskId,
              NULLIF(:workerTaskId, ''),
              :artifactKind,
              :storageBackend,
              :storageKey,
              :summary,
              CAST(:metadata AS jsonb)
            )
            """)
        .param("artifactId", artifactId)
        .param("taskId", taskId)
        .param("workerTaskId", workerTaskId == null ? "" : workerTaskId)
        .param("artifactKind", artifactKind)
        .param("storageBackend", storageBackend)
        .param("storageKey", storageKey)
        .param("summary", summary)
        .param("metadata", jsonSupport.write(metadata))
        .update();
    return getArtifact(artifactId);
  }

  public ArtifactRecord getArtifact(String artifactId) {
    return jdbcClient.sql("""
            SELECT
              artifact_id,
              task_id,
              worker_task_id,
              artifact_kind,
              storage_backend,
              storage_key,
              summary,
              metadata,
              created_at
            FROM agent_task_manager.task_artifacts
            WHERE artifact_id = :artifactId
            """)
        .param("artifactId", artifactId)
        .query((rs, rowNum) -> new ArtifactRecord(
            rs.getString("artifact_id"),
            rs.getString("task_id"),
            rs.getString("worker_task_id"),
            rs.getString("artifact_kind"),
            rs.getString("storage_backend"),
            rs.getString("storage_key"),
            rs.getString("summary"),
            jsonSupport.readMap(rs.getString("metadata")),
            rs.getObject("created_at", OffsetDateTime.class)
        ))
        .single();
  }

  public List<ArtifactRecord> listArtifacts(String taskId, String workerTaskId) {
    return jdbcClient.sql("""
            SELECT
              artifact_id,
              task_id,
              worker_task_id,
              artifact_kind,
              storage_backend,
              storage_key,
              summary,
              metadata,
              created_at
            FROM agent_task_manager.task_artifacts
            WHERE task_id = :taskId
              AND (:workerTaskId = '' OR COALESCE(worker_task_id, '') = :workerTaskId)
            ORDER BY created_at DESC
            """)
        .param("taskId", taskId)
        .param("workerTaskId", workerTaskId == null ? "" : workerTaskId)
        .query((rs, rowNum) -> new ArtifactRecord(
            rs.getString("artifact_id"),
            rs.getString("task_id"),
            rs.getString("worker_task_id"),
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

