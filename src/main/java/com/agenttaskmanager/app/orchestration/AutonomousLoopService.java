package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.config.OrchestrationProperties;
import java.nio.file.Path;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AutonomousLoopService {

  private final AutonomousCycleService autonomousCycleService;
  private final OrchestrationProperties orchestrationProperties;

  public AutonomousLoopService(
      AutonomousCycleService autonomousCycleService,
      OrchestrationProperties orchestrationProperties
  ) {
    this.autonomousCycleService = autonomousCycleService;
    this.orchestrationProperties = orchestrationProperties;
  }

  @Scheduled(
      initialDelayString = "${app.orchestration.autonomy-poll-interval-ms:15000}",
      fixedDelayString = "${app.orchestration.autonomy-poll-interval-ms:15000}"
  )
  public void runAutonomousLoop() {
    if (!orchestrationProperties.isAutonomyEnabled()) {
      return;
    }
    autonomousCycleService.runCycle(Path.of(".").toAbsolutePath());
  }
}
