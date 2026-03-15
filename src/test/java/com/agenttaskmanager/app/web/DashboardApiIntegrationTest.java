package com.agenttaskmanager.app.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenttaskmanager.app.dashboard.DashboardSummaryService;
import com.agenttaskmanager.app.orchestration.OverseerOrchestrationService;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

class DashboardApiIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private JdbcClient jdbcClient;

  @Autowired
  private DashboardSummaryService dashboardSummaryService;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private OverseerOrchestrationService overseerOrchestrationService;

  @BeforeEach
  void cleanup() {
    jdbcClient.sql("DELETE FROM agent_task_manager.validation_violations").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.validation_reports").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.patch_decisions").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.cleanup_reviews").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.shared_task_context").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.task_artifacts").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.worker_checkins").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.worker_task_leases").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.worker_tasks").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.agent_tasks WHERE task_kind = 'orchestration-batch'").update();
  }

  @Test
  void shouldExposeDashboardSummary() throws Exception {
    overseerOrchestrationService.createTaskBatch(
        "agent-task-manager",
        "/srv/AgentTaskManager",
        "Dashboard batch",
        true,
        List.of("implementation", "cleanup")
    );
    dashboardSummaryService.warmDashboardCache();

    mockMvc.perform(get("/api/dashboard/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.queuedTasks").value(2))
        .andExpect(jsonPath("$.batches[0].title").value("Dashboard batch"));
  }
}
