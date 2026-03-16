package com.agenttaskmanager.app.harness.state;

import com.agenttaskmanager.app.model.orchestration.WorkerCheckIn;
import com.agenttaskmanager.app.model.orchestration.WorkerLease;
import java.util.List;

public record HarnessAgentSchema(
    List<WorkerLease> activeLeases,
    List<WorkerCheckIn> workerCheckIns,
    List<HarnessWorkerSummary> workers
) {
}
