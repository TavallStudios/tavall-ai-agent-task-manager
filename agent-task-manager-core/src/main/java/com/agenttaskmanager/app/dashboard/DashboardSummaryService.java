package com.agenttaskmanager.app.dashboard;

import cache.CacheDomain;
import cache.CacheSource;
import cache.CacheType;
import cache.DashboardSummaryCache;
import com.agenttaskmanager.app.dashboard.model.ChatDashboardCard;
import com.agenttaskmanager.app.dashboard.model.DashboardSummary;
import com.agenttaskmanager.app.dashboard.model.PatchDecisionDashboardCard;
import com.agenttaskmanager.app.dashboard.model.TaskBatchDashboardCard;
import com.agenttaskmanager.app.dashboard.model.ValidationDashboardCard;
import com.agenttaskmanager.app.dashboard.model.WorkerDashboardCard;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class DashboardSummaryService {

  private final JdbcClient jdbcClient;
  private final DashboardSummaryCache dashboardSummaryCache;

  public DashboardSummaryService(JdbcClient jdbcClient, DashboardSummaryCache dashboardSummaryCache) {
    this.jdbcClient = jdbcClient;
    this.dashboardSummaryCache = dashboardSummaryCache;
  }

  public DashboardSummary loadDashboardSummary() {
    Map<String, Object> cached = dashboardSummaryCache.getOrLoad(
        "default",
        CacheDomain.DASHBOARD,
        CacheType.DASHBOARD_SUMMARY,
        CacheSource.POSTGRES,
        this::queryDashboardPayload
    );
    return toSummary(cached);
  }

  public DashboardSummary warmDashboardCache() {
    Map<String, Object> payload = queryDashboardPayload();
    dashboardSummaryCache.put(
        "default",
        CacheDomain.DASHBOARD,
        CacheType.DASHBOARD_SUMMARY,
        CacheSource.POSTGRES,
        payload,
        30_000L
    );
    return toSummary(payload);
  }

  private Map<String, Object> queryDashboardPayload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("activeChats", count("""
        SELECT count(*) FROM agent_task_manager.prompt_thread_overview
        WHERE COALESCE(last_message_at, updated_at) > now() - interval '10 minutes'
        """));
    payload.put("deadChats", count("""
        SELECT count(*) FROM agent_task_manager.prompt_thread_overview
        WHERE COALESCE(last_message_at, updated_at) <= now() - interval '10 minutes'
        """));
    payload.put("activeWorkers", count("""
        SELECT count(*) FROM agent_task_manager.worker_task_overview
        WHERE status IN ('ASSIGNED', 'RUNNING', 'CHECKED_IN', 'UNDER_REVIEW')
        """));
    payload.put("deadWorkers", count("""
        SELECT count(*) FROM agent_task_manager.worker_task_overview
        WHERE status IN ('DEAD', 'DEAD_LETTER')
        """));
    payload.put("queuedTasks", count("""
        SELECT count(*) FROM agent_task_manager.worker_tasks
        WHERE status IN ('QUEUED', 'REASSIGNED', 'NEEDS_REWORK')
        """));
    payload.put("runningTasks", count("""
        SELECT count(*) FROM agent_task_manager.worker_tasks
        WHERE status IN ('ASSIGNED', 'RUNNING', 'CHECKED_IN', 'UNDER_REVIEW')
        """));
    payload.put("failedTasks", count("""
        SELECT count(*) FROM agent_task_manager.worker_tasks
        WHERE status IN ('FAILED', 'DEAD', 'DEAD_LETTER')
        """));
    payload.put("completedTasks", count("""
        SELECT count(*) FROM agent_task_manager.worker_tasks
        WHERE status IN ('COMPLETED', 'APPROVED')
        """));
    payload.put("cleanupReviewsPending", count("""
        SELECT count(*) FROM agent_task_manager.cleanup_reviews
        WHERE status IN ('UNDER_REVIEW', 'QUEUED')
        """));
    payload.put("patchRejections", count("""
        SELECT count(*) FROM agent_task_manager.patch_decisions
        WHERE status IN ('FAILED', 'NEEDS_REWORK')
        """));
    payload.put("chats", queryChats());
    payload.put("workers", queryWorkers());
    payload.put("batches", queryBatches());
    payload.put("validations", queryValidations());
    payload.put("patchDecisions", queryPatchDecisions());
    payload.put("cacheStats", Map.of("dashboard", dashboardSummaryCache.getCacheStats()));
    return payload;
  }

  private List<ChatDashboardCard> queryChats() {
    return jdbcClient.sql("""
            SELECT
              thread_key,
              repo_path,
              bridge_target,
              latest_request_status,
              last_message_at,
              COALESCE(last_message_at, updated_at) <= now() - interval '10 minutes' AS dead
            FROM agent_task_manager.prompt_thread_overview
            ORDER BY COALESCE(last_message_at, updated_at) DESC
            LIMIT 8
            """)
        .query((rs, rowNum) -> new ChatDashboardCard(
            rs.getString("thread_key"),
            rs.getString("repo_path"),
            rs.getString("bridge_target"),
            rs.getString("latest_request_status"),
            rs.getObject("last_message_at", OffsetDateTime.class),
            rs.getBoolean("dead")
        ))
        .list();
  }

  private List<WorkerDashboardCard> queryWorkers() {
    return jdbcClient.sql("""
            SELECT
              worker_task_id,
              task_id,
              task_role,
              status,
              assigned_agent_id,
              assigned_transport,
              last_check_in_at,
              active_lease_expires_at,
              status IN ('DEAD', 'DEAD_LETTER') AS dead
            FROM agent_task_manager.worker_task_overview
            ORDER BY updated_at DESC
            LIMIT 8
            """)
        .query((rs, rowNum) -> new WorkerDashboardCard(
            rs.getString("worker_task_id"),
            rs.getString("task_id"),
            rs.getString("task_role"),
            rs.getString("status"),
            rs.getString("assigned_agent_id"),
            rs.getString("assigned_transport"),
            rs.getObject("last_check_in_at", OffsetDateTime.class),
            rs.getObject("active_lease_expires_at", OffsetDateTime.class),
            rs.getBoolean("dead")
        ))
        .list();
  }

  private List<TaskBatchDashboardCard> queryBatches() {
    return jdbcClient.sql("""
            SELECT
              task.task_id,
              task.project_key,
              task.title,
              task.status,
              task.updated_at,
              count(*) FILTER (WHERE worker.status IN ('QUEUED', 'REASSIGNED', 'NEEDS_REWORK')) AS queued_tasks,
              count(*) FILTER (WHERE worker.status IN ('ASSIGNED', 'RUNNING', 'CHECKED_IN', 'UNDER_REVIEW')) AS running_tasks,
              count(*) FILTER (WHERE worker.status IN ('FAILED', 'DEAD', 'DEAD_LETTER')) AS failed_tasks,
              count(*) FILTER (WHERE worker.status IN ('COMPLETED', 'APPROVED')) AS completed_tasks
            FROM agent_task_manager.agent_tasks AS task
            LEFT JOIN agent_task_manager.worker_tasks AS worker
              ON worker.task_id = task.task_id
            WHERE task.task_kind = 'orchestration-batch'
            GROUP BY task.task_id, task.project_key, task.title, task.status, task.updated_at
            ORDER BY task.updated_at DESC
            LIMIT 8
            """)
        .query((rs, rowNum) -> new TaskBatchDashboardCard(
            rs.getString("task_id"),
            rs.getString("project_key"),
            rs.getString("title"),
            rs.getString("status"),
            rs.getLong("queued_tasks"),
            rs.getLong("running_tasks"),
            rs.getLong("failed_tasks"),
            rs.getLong("completed_tasks"),
            rs.getObject("updated_at", OffsetDateTime.class)
        ))
        .list();
  }

  private List<ValidationDashboardCard> queryValidations() {
    return jdbcClient.sql("""
            SELECT
              task_id,
              worker_task_id,
              status,
              compliance_score,
              summary,
              completed_at
            FROM agent_task_manager.validation_reports
            ORDER BY updated_at DESC
            LIMIT 8
            """)
        .query((rs, rowNum) -> new ValidationDashboardCard(
            rs.getString("task_id"),
            rs.getString("worker_task_id"),
            rs.getString("status"),
            rs.getDouble("compliance_score"),
            rs.getString("summary"),
            rs.getObject("completed_at", OffsetDateTime.class)
        ))
        .list();
  }

  private List<PatchDecisionDashboardCard> queryPatchDecisions() {
    return jdbcClient.sql("""
            SELECT task_id, worker_task_id, status, summary, decision_by, updated_at
            FROM agent_task_manager.patch_decisions
            ORDER BY updated_at DESC
            LIMIT 8
            """)
        .query((rs, rowNum) -> new PatchDecisionDashboardCard(
            rs.getString("task_id"),
            rs.getString("worker_task_id"),
            rs.getString("status"),
            rs.getString("summary"),
            rs.getString("decision_by"),
            rs.getObject("updated_at", OffsetDateTime.class)
        ))
        .list();
  }

  private long count(String sql) {
    Long value = jdbcClient.sql(sql).query(Long.class).single();
    return value == null ? 0L : value;
  }

  @SuppressWarnings("unchecked")
  private DashboardSummary toSummary(Map<String, Object> payload) {
    return new DashboardSummary(
        ((Number) payload.get("activeChats")).longValue(),
        ((Number) payload.get("deadChats")).longValue(),
        ((Number) payload.get("activeWorkers")).longValue(),
        ((Number) payload.get("deadWorkers")).longValue(),
        ((Number) payload.get("queuedTasks")).longValue(),
        ((Number) payload.get("runningTasks")).longValue(),
        ((Number) payload.get("failedTasks")).longValue(),
        ((Number) payload.get("completedTasks")).longValue(),
        ((Number) payload.get("cleanupReviewsPending")).longValue(),
        ((Number) payload.get("patchRejections")).longValue(),
        (List<ChatDashboardCard>) payload.get("chats"),
        (List<WorkerDashboardCard>) payload.get("workers"),
        (List<TaskBatchDashboardCard>) payload.get("batches"),
        (List<ValidationDashboardCard>) payload.get("validations"),
        (List<PatchDecisionDashboardCard>) payload.get("patchDecisions"),
        (Map<String, Object>) payload.get("cacheStats")
    );
  }
}
