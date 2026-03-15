package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.orchestration.PatchDecisionRecord;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PatchDecisionRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public PatchDecisionRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public PatchDecisionRecord storeDecision(
      String taskId,
      String workerTaskId,
      String validationReportId,
      String cleanupReviewId,
      String diffArtifactId,
      TaskLifecycleStatus status,
      String summary,
      String decisionBy,
      Map<String, Object> metadata
  ) {
    String patchDecisionId = "pd_" + UUID.randomUUID();
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.patch_decisions (
              patch_decision_id,
              task_id,
              worker_task_id,
              validation_report_id,
              cleanup_review_id,
              diff_artifact_id,
              status,
              summary,
              decision_by,
              metadata
            ) VALUES (
              :patchDecisionId,
              :taskId,
              NULLIF(:workerTaskId, ''),
              NULLIF(:validationReportId, ''),
              NULLIF(:cleanupReviewId, ''),
              NULLIF(:diffArtifactId, ''),
              :status,
              :summary,
              NULLIF(:decisionBy, ''),
              CAST(:metadata AS jsonb)
            )
            """)
        .param("patchDecisionId", patchDecisionId)
        .param("taskId", taskId)
        .param("workerTaskId", workerTaskId == null ? "" : workerTaskId)
        .param("validationReportId", validationReportId == null ? "" : validationReportId)
        .param("cleanupReviewId", cleanupReviewId == null ? "" : cleanupReviewId)
        .param("diffArtifactId", diffArtifactId == null ? "" : diffArtifactId)
        .param("status", status.name())
        .param("summary", summary)
        .param("decisionBy", decisionBy == null ? "" : decisionBy)
        .param("metadata", jsonSupport.write(metadata))
        .update();
    return getDecision(patchDecisionId);
  }

  public PatchDecisionRecord getDecision(String patchDecisionId) {
    return jdbcClient.sql("""
            SELECT
              patch_decision_id,
              task_id,
              worker_task_id,
              validation_report_id,
              cleanup_review_id,
              diff_artifact_id,
              status,
              summary,
              decision_by,
              metadata,
              created_at,
              updated_at
            FROM agent_task_manager.patch_decisions
            WHERE patch_decision_id = :patchDecisionId
            """)
        .param("patchDecisionId", patchDecisionId)
        .query((rs, rowNum) -> new PatchDecisionRecord(
            rs.getString("patch_decision_id"),
            rs.getString("task_id"),
            rs.getString("worker_task_id"),
            rs.getString("validation_report_id"),
            rs.getString("cleanup_review_id"),
            rs.getString("diff_artifact_id"),
            TaskLifecycleStatus.valueOf(rs.getString("status")),
            rs.getString("summary"),
            rs.getString("decision_by"),
            jsonSupport.readMap(rs.getString("metadata")),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        ))
        .single();
  }

  public List<PatchDecisionRecord> listByTask(String taskId) {
    return jdbcClient.sql("""
            SELECT
              patch_decision_id,
              task_id,
              worker_task_id,
              validation_report_id,
              cleanup_review_id,
              diff_artifact_id,
              status,
              summary,
              decision_by,
              metadata,
              created_at,
              updated_at
            FROM agent_task_manager.patch_decisions
            WHERE task_id = :taskId
            ORDER BY updated_at DESC
            """)
        .param("taskId", taskId)
        .query((rs, rowNum) -> new PatchDecisionRecord(
            rs.getString("patch_decision_id"),
            rs.getString("task_id"),
            rs.getString("worker_task_id"),
            rs.getString("validation_report_id"),
            rs.getString("cleanup_review_id"),
            rs.getString("diff_artifact_id"),
            TaskLifecycleStatus.valueOf(rs.getString("status")),
            rs.getString("summary"),
            rs.getString("decision_by"),
            jsonSupport.readMap(rs.getString("metadata")),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        ))
        .list();
  }
}
