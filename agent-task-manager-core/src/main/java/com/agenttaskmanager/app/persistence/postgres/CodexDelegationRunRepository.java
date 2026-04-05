package com.agenttaskmanager.app.persistence.postgres;

import com.agenttaskmanager.app.model.orchestration.CodexDelegationRun;
import com.agenttaskmanager.app.model.orchestration.CodexDelegationStep;
import com.agenttaskmanager.app.model.orchestration.TaskLifecycleStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CodexDelegationRunRepository {

  private final JdbcClient jdbcClient;
  private final JsonSupport jsonSupport;

  public CodexDelegationRunRepository(JdbcClient jdbcClient, JsonSupport jsonSupport) {
    this.jdbcClient = jdbcClient;
    this.jsonSupport = jsonSupport;
  }

  public CodexDelegationRun createRun(
      String taskId,
      String projectKey,
      String repoPath,
      String title,
      TaskLifecycleStatus status,
      String summary,
      Map<String, Object> metadata
  ) {
    String runId = "cdr_" + UUID.randomUUID();
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.codex_delegation_runs (
              run_id,
              task_id,
              project_key,
              repo_path,
              title,
              status,
              summary,
              metadata
            ) VALUES (
              :runId,
              NULLIF(:taskId, ''),
              NULLIF(:projectKey, ''),
              NULLIF(:repoPath, ''),
              :title,
              :status,
              :summary,
              CAST(:metadata AS jsonb)
            )
            """)
        .param("runId", runId)
        .param("taskId", blank(taskId))
        .param("projectKey", blank(projectKey))
        .param("repoPath", blank(repoPath))
        .param("title", title)
        .param("status", status.name())
        .param("summary", summary == null ? "" : summary)
        .param("metadata", jsonSupport.write(metadata == null ? Map.of() : metadata))
        .update();
    return getRun(runId);
  }

  public CodexDelegationStep appendStep(
      String runId,
      String eventType,
      TaskLifecycleStatus status,
      String summary,
      Map<String, Object> details
  ) {
    String stepId = "cds_" + UUID.randomUUID();
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.codex_delegation_steps (
              step_id,
              run_id,
              event_type,
              status,
              summary,
              details
            ) VALUES (
              :stepId,
              :runId,
              :eventType,
              :status,
              :summary,
              CAST(:details AS jsonb)
            )
            """)
        .param("stepId", stepId)
        .param("runId", runId)
        .param("eventType", eventType)
        .param("status", status.name())
        .param("summary", summary == null ? "" : summary)
        .param("details", jsonSupport.write(details == null ? Map.of() : details))
        .update();
    return getStep(stepId);
  }

  public CodexDelegationRun updateRunStatus(
      String runId,
      TaskLifecycleStatus status,
      String summary,
      Map<String, Object> metadata
  ) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.codex_delegation_runs
            SET status = :status,
                summary = :summary,
                metadata = CAST(:metadata AS jsonb),
                completed_at = CASE
                  WHEN :status IN ('COMPLETED', 'FAILED', 'DEAD', 'DEAD_LETTER', 'APPROVED', 'NEEDS_REWORK')
                  THEN now()
                  ELSE completed_at
                END,
                updated_at = now()
            WHERE run_id = :runId
            """)
        .param("runId", runId)
        .param("status", status.name())
        .param("summary", summary == null ? "" : summary)
        .param("metadata", jsonSupport.write(metadata == null ? Map.of() : metadata))
        .update();
    return getRun(runId);
  }

  public CodexDelegationRun getRun(String runId) {
    return jdbcClient.sql("""
            SELECT
              run_id,
              task_id,
              project_key,
              repo_path,
              title,
              status,
              summary,
              metadata,
              created_at,
              updated_at,
              completed_at
            FROM agent_task_manager.codex_delegation_runs
            WHERE run_id = :runId
            """)
        .param("runId", runId)
        .query((rs, rowNum) -> mapRun(rs))
        .single();
  }

  public Optional<CodexDelegationRun> findLatestByTaskId(String taskId) {
    return jdbcClient.sql("""
            SELECT
              run_id,
              task_id,
              project_key,
              repo_path,
              title,
              status,
              summary,
              metadata,
              created_at,
              updated_at,
              completed_at
            FROM agent_task_manager.codex_delegation_runs
            WHERE task_id = :taskId
            ORDER BY updated_at DESC, created_at DESC
            LIMIT 1
            """)
        .param("taskId", taskId)
        .query((rs, rowNum) -> mapRun(rs))
        .optional();
  }

  public List<CodexDelegationRun> listRuns(int limit, String status) {
    if (status == null || status.isBlank()) {
      return jdbcClient.sql("""
              SELECT
                run_id,
                task_id,
                project_key,
                repo_path,
                title,
                status,
                summary,
                metadata,
                created_at,
                updated_at,
                completed_at
              FROM agent_task_manager.codex_delegation_runs
              ORDER BY updated_at DESC, created_at DESC
              LIMIT :limit
              """)
          .param("limit", limit)
          .query((rs, rowNum) -> mapRun(rs))
          .list();
    }
    return jdbcClient.sql("""
            SELECT
              run_id,
              task_id,
              project_key,
              repo_path,
              title,
              status,
              summary,
              metadata,
              created_at,
              updated_at,
              completed_at
            FROM agent_task_manager.codex_delegation_runs
            WHERE status = :status
            ORDER BY updated_at DESC, created_at DESC
            LIMIT :limit
            """)
        .param("status", status.strip().toUpperCase())
        .param("limit", limit)
        .query((rs, rowNum) -> mapRun(rs))
        .list();
  }

  public List<CodexDelegationStep> listSteps(String runId) {
    return jdbcClient.sql("""
            SELECT
              step_id,
              run_id,
              event_type,
              status,
              summary,
              details,
              created_at
            FROM agent_task_manager.codex_delegation_steps
            WHERE run_id = :runId
            ORDER BY created_at ASC
            """)
        .param("runId", runId)
        .query((rs, rowNum) -> mapStep(rs))
        .list();
  }

  private CodexDelegationStep getStep(String stepId) {
    return jdbcClient.sql("""
            SELECT
              step_id,
              run_id,
              event_type,
              status,
              summary,
              details,
              created_at
            FROM agent_task_manager.codex_delegation_steps
            WHERE step_id = :stepId
            """)
        .param("stepId", stepId)
        .query((rs, rowNum) -> mapStep(rs))
        .single();
  }

  private CodexDelegationRun mapRun(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new CodexDelegationRun(
        rs.getString("run_id"),
        rs.getString("task_id"),
        rs.getString("project_key"),
        rs.getString("repo_path"),
        rs.getString("title"),
        TaskLifecycleStatus.valueOf(rs.getString("status")),
        rs.getString("summary"),
        jsonSupport.readMap(rs.getString("metadata")),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class),
        rs.getObject("completed_at", OffsetDateTime.class)
    );
  }

  private CodexDelegationStep mapStep(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new CodexDelegationStep(
        rs.getString("step_id"),
        rs.getString("run_id"),
        rs.getString("event_type"),
        TaskLifecycleStatus.valueOf(rs.getString("status")),
        rs.getString("summary"),
        jsonSupport.readMap(rs.getString("details")),
        rs.getObject("created_at", OffsetDateTime.class)
    );
  }

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }
}
