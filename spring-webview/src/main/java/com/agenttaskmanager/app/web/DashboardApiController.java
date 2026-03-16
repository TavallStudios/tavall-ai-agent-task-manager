package com.agenttaskmanager.app.web;

import com.agenttaskmanager.app.dashboard.DashboardSummaryService;
import com.agenttaskmanager.app.dashboard.model.DashboardSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {

  private final DashboardSummaryService dashboardSummaryService;

  public DashboardApiController(DashboardSummaryService dashboardSummaryService) {
    this.dashboardSummaryService = dashboardSummaryService;
  }

  @GetMapping("/summary")
  public DashboardSummary summary() {
    return dashboardSummaryService.loadDashboardSummary();
  }
}
