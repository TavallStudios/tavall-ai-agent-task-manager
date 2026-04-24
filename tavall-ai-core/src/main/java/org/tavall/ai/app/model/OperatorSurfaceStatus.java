package org.tavall.ai.app.model;

import java.time.OffsetDateTime;
import java.util.List;

public record OperatorSurfaceStatus(
    OffsetDateTime checkedAt,
    List<String> repoRoots,
    List<String> failoverSteps,
    List<OperatorToolCard> tools
) {
}

