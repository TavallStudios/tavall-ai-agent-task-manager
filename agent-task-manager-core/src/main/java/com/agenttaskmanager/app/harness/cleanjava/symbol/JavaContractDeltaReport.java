package com.agenttaskmanager.app.harness.cleanjava.symbol;

import java.util.List;

public record JavaContractDeltaReport(
    String status,
    boolean risky,
    boolean reflectionAugmented,
    List<String> changedSourcePaths,
    List<String> targetedClasses,
    List<JavaContractChange> changes,
    String summary
) {
}
