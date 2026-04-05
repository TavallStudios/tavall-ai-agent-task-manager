package com.agenttaskmanager.app.harness.cleanjava.symbol;

public record JavaContractChange(
    String kind,
    String target,
    String detail,
    boolean risky
) {
}
