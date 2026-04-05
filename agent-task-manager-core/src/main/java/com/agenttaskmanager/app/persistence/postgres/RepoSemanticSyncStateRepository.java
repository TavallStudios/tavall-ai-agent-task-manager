package com.agenttaskmanager.app.persistence.postgres;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RepoSemanticSyncStateRepository {

  private final JdbcClient jdbcClient;

  public RepoSemanticSyncStateRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public Optional<RepoSemanticSyncState> find(String projectKey) {
    return jdbcClient.sql("""
            SELECT
              project_key,
              repo_path,
              last_synced_head,
              last_synced_at,
              last_scan_started_at,
              last_scan_completed_at,
              last_error
            FROM agent_task_manager.repo_semantic_sync_state
            WHERE project_key = :projectKey
            """)
        .param("projectKey", projectKey)
        .query((rs, rowNum) -> new RepoSemanticSyncState(
            rs.getString("project_key"),
            rs.getString("repo_path"),
            rs.getString("last_synced_head"),
            rs.getObject("last_synced_at", OffsetDateTime.class),
            rs.getObject("last_scan_started_at", OffsetDateTime.class),
            rs.getObject("last_scan_completed_at", OffsetDateTime.class),
            rs.getString("last_error")
        ))
        .optional();
  }

  public void markScanStarted(String projectKey, String repoPath) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.repo_semantic_sync_state (
              project_key,
              repo_path,
              last_scan_started_at,
              last_error
            ) VALUES (
              :projectKey,
              :repoPath,
              now(),
              NULL
            )
            ON CONFLICT (project_key) DO UPDATE SET
              repo_path = EXCLUDED.repo_path,
              last_scan_started_at = now(),
              last_error = NULL
            """)
        .param("projectKey", projectKey)
        .param("repoPath", repoPath)
        .update();
  }

  public void markScanSuccess(String projectKey, String repoPath, String headCommitHash) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.repo_semantic_sync_state (
              project_key,
              repo_path,
              last_synced_head,
              last_synced_at,
              last_scan_completed_at,
              last_error
            ) VALUES (
              :projectKey,
              :repoPath,
              :headCommitHash,
              now(),
              now(),
              NULL
            )
            ON CONFLICT (project_key) DO UPDATE SET
              repo_path = EXCLUDED.repo_path,
              last_synced_head = EXCLUDED.last_synced_head,
              last_synced_at = now(),
              last_scan_completed_at = now(),
              last_error = NULL
            """)
        .param("projectKey", projectKey)
        .param("repoPath", repoPath)
        .param("headCommitHash", blank(headCommitHash))
        .update();
  }

  public void markScanFailure(String projectKey, String repoPath, String errorMessage) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.repo_semantic_sync_state (
              project_key,
              repo_path,
              last_error,
              last_scan_completed_at
            ) VALUES (
              :projectKey,
              :repoPath,
              :errorMessage,
              now()
            )
            ON CONFLICT (project_key) DO UPDATE SET
              repo_path = EXCLUDED.repo_path,
              last_error = EXCLUDED.last_error,
              last_scan_completed_at = now()
            """)
        .param("projectKey", projectKey)
        .param("repoPath", repoPath)
        .param("errorMessage", blank(errorMessage))
        .update();
  }

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }
}
