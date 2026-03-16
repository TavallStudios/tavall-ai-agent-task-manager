package com.agenttaskmanager.app.harness.routing;

import com.agenttaskmanager.app.harness.intake.ParentTaskRequest;
import java.util.List;

public record HarnessRoutingPlan(
    ParentTaskRequest request,
    List<HarnessWorkerPlan> workerPlans,
    String summary
) {
}
