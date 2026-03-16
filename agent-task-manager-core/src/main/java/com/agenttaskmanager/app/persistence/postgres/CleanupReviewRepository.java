package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.orchestration.CleanupReviewResult;
import com.agenttaskmanager.app.model.orchestration.CleanupReviewTask;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CleanupReviewRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public CleanupReviewRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public CleanupReviewTask createReviewTask(
      String taskId,
      String workerTaskId,
      String reviewerAgentId,
      String diffArtifactId
  ) {
    String cleanupReviewId = "cr_" + UUID.randomUUID();
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.cleanup_reviews (
              cleanup_review_id,
              task_id,
              worker_task_id,
              reviewer_agent_id,
              status,
              diff_artifact_id,
              findings
            ) VALUES (
              :cleanupReviewId,
              :taskId,
              NULLIF(:workerTaskId, ''),
              NULLIF(:reviewerAgentId, ''),
              'UNDER_REVIEW',
              NULLIF(:diffArtifactId, ''),
              '[]'::jsonb
            )
            """)
        .param("cleanupReviewId", cleanupReviewId)
        .param("taskId", taskId)
        .param("workerTaskId", workerTaskId == null ? "" : workerTaskId)
        .param("reviewerAgentId", reviewerAgentId == null ? "" : reviewerAgentId)
        .param("diffArtifactId", diffArtifactId == null ? "" : diffArtifactId)
        .update();
    return getReviewTask(cleanupReviewId);
  }

  public CleanupReviewResult completeReview(
      String cleanupReviewId,
      TaskLifecycleStatus status,
      String summary,
      List<String> findings
  ) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.cleanup_reviews
            SET status = :status,
                summary = :summary,
                findings = CAST(:findings AS jsonb),
                completed_at = now(),
                updated_at = now()
            WHERE cleanup_review_id = :cleanupReviewId
            """)
        .param("status", status.name())
        .param("summary", summary)
        .param("findings", jsonSupport.write(findings))
        .param("cleanupReviewId", cleanupReviewId)
        .update();
    return getReviewResult(cleanupReviewId);
  }

  public CleanupReviewTask getReviewTask(String cleanupReviewId) {
    return jdbcClient.sql("""
            SELECT cleanup_review_id, task_id, worker_task_id, diff_artifact_id, status
            FROM agent_task_manager.cleanup_reviews
            WHERE cleanup_review_id = :cleanupReviewId
            """)
        .param("cleanupReviewId", cleanupReviewId)
        .query((rs, rowNum) -> new CleanupReviewTask(
            rs.getString("cleanup_review_id"),
            rs.getString("task_id"),
            rs.getString("worker_task_id"),
            rs.getString("diff_artifact_id"),
            TaskLifecycleStatus.valueOf(rs.getString("status"))
        ))
        .single();
  }

  public CleanupReviewResult getReviewResult(String cleanupReviewId) {
    return jdbcClient.sql("""
            SELECT
              cleanup_review_id,
              task_id,
              worker_task_id,
              status,
              summary,
              findings,
              completed_at
            FROM agent_task_manager.cleanup_reviews
            WHERE cleanup_review_id = :cleanupReviewId
            """)
        .param("cleanupReviewId", cleanupReviewId)
        .query((rs, rowNum) -> new CleanupReviewResult(
            rs.getString("cleanup_review_id"),
            rs.getString("task_id"),
            rs.getString("worker_task_id"),
            TaskLifecycleStatus.valueOf(rs.getString("status")),
            rs.getString("summary"),
            jsonSupport.readStringList(rs.getString("findings")),
            rs.getObject("completed_at", OffsetDateTime.class)
        ))
        .single();
  }

  public List<CleanupReviewResult> listByTask(String taskId) {
    return jdbcClient.sql("""
            SELECT
              cleanup_review_id,
              task_id,
              worker_task_id,
              status,
              summary,
              findings,
              completed_at
            FROM agent_task_manager.cleanup_reviews
            WHERE task_id = :taskId
            ORDER BY updated_at DESC, created_at DESC
            """)
        .param("taskId", taskId)
        .query((rs, rowNum) -> new CleanupReviewResult(
            rs.getString("cleanup_review_id"),
            rs.getString("task_id"),
            rs.getString("worker_task_id"),
            TaskLifecycleStatus.valueOf(rs.getString("status")),
            rs.getString("summary"),
            jsonSupport.readStringList(rs.getString("findings")),
            rs.getObject("completed_at", OffsetDateTime.class)
        ))
        .list();
  }
}
