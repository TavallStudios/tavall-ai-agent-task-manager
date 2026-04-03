package com.agenttaskmanager.app.model.computeruse;

import java.util.List;
import java.util.Map;

public record ComputerUseRunnerRegistration(
    String runnerId,
    String displayName,
    String hostName,
    String baseUrl,
    String launcherPath,
    String clientPath,
    List<String> supportedCaptureModes,
    Map<String, Object> capabilities,
    Map<String, Object> metadata
) {
}
