package org.tavall.ai.app.model.orchestration;

import java.nio.file.Path;

public record WorkerExecutionRequest(
    String taskId,
    String workerTaskId,
    String agentId,
    String sessionId,
    Path repoPath
) {
}

