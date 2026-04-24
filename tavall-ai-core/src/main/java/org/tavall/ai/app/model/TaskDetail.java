package org.tavall.ai.app.model;

import java.util.List;

public record TaskDetail(TaskSummary task, List<TaskCheckpoint> checkpoints) {
}


