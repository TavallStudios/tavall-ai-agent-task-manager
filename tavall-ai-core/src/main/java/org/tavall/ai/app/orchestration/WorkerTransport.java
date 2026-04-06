package org.tavall.ai.app.orchestration;

import org.tavall.ai.app.model.orchestration.WorkerExecutionRequest;
import org.tavall.ai.app.model.orchestration.WorkerExecutionResult;

public interface WorkerTransport {

  WorkerExecutionResult executeWorkerTask(WorkerExecutionRequest request);
}

