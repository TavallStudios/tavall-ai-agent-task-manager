package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.memory.MemoryTurnHandle;

public record WorkerPromptRunHandle(
    String requestId,
    long runId,
    String threadKey,
    String threadSessionId,
    MemoryTurnHandle memoryTurnHandle
) {

  public WorkerPromptRunHandle withThreadSessionId(String value) {
    return new WorkerPromptRunHandle(requestId, runId, threadKey, value == null ? "" : value, memoryTurnHandle);
  }
}
