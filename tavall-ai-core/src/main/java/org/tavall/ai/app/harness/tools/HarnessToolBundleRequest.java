package org.tavall.ai.app.harness.tools;

public record HarnessToolBundleRequest(
    String bundleName,
    String taskId,
    String workerTaskId,
    String projectKey,
    String repoPath,
    String queryText,
    Integer limit
) {

  public HarnessToolBundleType bundleType() {
    return HarnessToolBundleType.fromValue(bundleName);
  }
}

