package org.tavall.ai.app.model.computeruse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ComputerUseRunnerSummary(
    String runnerId,
    String displayName,
    String hostName,
    String baseUrl,
    String launcherPath,
    String clientPath,
    String status,
    String currentLeaseSessionId,
    List<String> supportedCaptureModes,
    Map<String, Object> capabilities,
    Map<String, Object> metadata,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime lastSeenAt
) {
}

