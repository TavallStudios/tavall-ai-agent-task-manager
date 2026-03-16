package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.model.orchestration.WorkerExecutionRequest;
import com.agenttaskmanager.app.model.orchestration.WorkerExecutionResult;

public interface WorkerTransport {

  WorkerExecutionResult executeWorkerTask(WorkerExecutionRequest request);
}
