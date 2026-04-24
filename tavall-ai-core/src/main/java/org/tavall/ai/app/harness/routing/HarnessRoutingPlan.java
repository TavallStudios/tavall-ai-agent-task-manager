package org.tavall.ai.app.harness.routing;

import org.tavall.ai.app.harness.intake.ParentTaskRequest;
import java.util.List;

public record HarnessRoutingPlan(
    ParentTaskRequest request,
    List<HarnessWorkerPlan> workerPlans,
    String summary
) {
}

