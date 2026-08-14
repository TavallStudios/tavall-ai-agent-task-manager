package org.tavall.ai.execution.model.codex;

import org.tavall.ai.execution.model.TavallAIModelExecutionRequest;

import java.nio.file.Path;

/** Resolves the already-authorized workspace lease for one Codex model execution. */
@FunctionalInterface
public interface CodexWorkspaceResolver {
    Path resolve(TavallAIModelExecutionRequest request);
}
