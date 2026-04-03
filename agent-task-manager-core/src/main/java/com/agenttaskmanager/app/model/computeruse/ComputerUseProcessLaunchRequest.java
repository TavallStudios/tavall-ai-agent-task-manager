package com.agenttaskmanager.app.model.computeruse;

public record ComputerUseProcessLaunchRequest(
    String sessionId,
    String launchTarget,
    String fileName,
    String arguments,
    String workingDirectory,
    int waitForWindowMs,
    String windowTitleContains
) {
}
