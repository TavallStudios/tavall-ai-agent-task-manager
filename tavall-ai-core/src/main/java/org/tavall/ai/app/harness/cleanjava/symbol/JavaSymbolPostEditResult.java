package org.tavall.ai.app.harness.cleanjava.symbol;

import java.util.List;

public record JavaSymbolPostEditResult(
    String status,
    boolean reflectionAugmented,
    List<String> changedSourcePaths,
    List<JavaClassProfile> currentProfiles,
    JavaContractDeltaReport contractDeltaReport,
    List<String> warnings,
    String artifactId,
    String artifactSummary
) {

  public boolean gatePassed() {
    return contractDeltaReport == null || !contractDeltaReport.risky();
  }
}

