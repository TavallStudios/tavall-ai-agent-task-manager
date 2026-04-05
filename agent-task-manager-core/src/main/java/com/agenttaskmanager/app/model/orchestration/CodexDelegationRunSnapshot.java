package com.agenttaskmanager.app.model.orchestration;

import java.util.List;

public record CodexDelegationRunSnapshot(
    CodexDelegationRun run,
    List<CodexDelegationStep> steps
) {
}
