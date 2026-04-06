package org.tavall.ai.app.mcp.tools.artifact;

import org.tavall.ai.app.model.orchestration.ArtifactRecord;
import java.util.List;
import java.util.Map;

record ArtifactIdRequest(String artifactId) {
}

record WriteArtifactRequest(
    String taskId,
    String workerTaskId,
    String artifactKind,
    String summary,
    String body,
    Map<String, Object> metadata
) {
}

record LoadArtifactsRequest(String taskId, String workerTaskId) {
}

record StoreDiffArtifactRequest(String taskId, String workerTaskId, String diffBody) {
}

record ArtifactRecordResponse(ArtifactRecord artifact) {
}

record ArtifactListResponse(List<ArtifactRecord> artifacts) {
}

record ArtifactBodyResponse(String body) {
}

