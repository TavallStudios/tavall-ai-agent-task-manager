package com.agenttaskmanager.app.model.computeruse;

import java.util.Map;

public record ComputerUseProcessLaunchResult(
    String fileName,
    Map<String, Object> runnerResult
) {
}
