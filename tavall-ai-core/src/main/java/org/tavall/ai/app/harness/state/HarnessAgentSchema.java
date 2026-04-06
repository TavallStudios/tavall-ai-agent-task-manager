package org.tavall.ai.app.harness.state;

import org.tavall.ai.app.model.orchestration.WorkerCheckIn;
import org.tavall.ai.app.model.orchestration.WorkerLease;
import java.util.List;

public record HarnessAgentSchema(
    List<WorkerLease> activeLeases,
    List<WorkerCheckIn> workerCheckIns,
    List<HarnessWorkerSummary> workers
) {
}

