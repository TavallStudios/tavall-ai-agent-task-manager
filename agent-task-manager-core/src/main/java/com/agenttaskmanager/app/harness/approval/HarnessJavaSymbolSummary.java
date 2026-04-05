package com.agenttaskmanager.app.harness.approval;

public record HarnessJavaSymbolSummary(
    String javaSymbolStatus,
    boolean reflectionAugmented,
    String contractDeltaStatus,
    String contractDeltaSummary,
    String contractDeltaArtifactId
) {
}
