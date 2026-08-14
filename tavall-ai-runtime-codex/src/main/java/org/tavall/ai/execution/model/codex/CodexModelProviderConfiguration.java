package org.tavall.ai.execution.model.codex;

import java.nio.file.Path;
import java.util.Objects;

/** Host-side configuration for the Tavall AI Codex model provider. */
public record CodexModelProviderConfiguration(
        Path executable,
        CodexSandboxMode sandboxMode
) {
    public CodexModelProviderConfiguration {
        executable = Objects.requireNonNull(executable, "executable").toAbsolutePath().normalize();
        sandboxMode = Objects.requireNonNull(sandboxMode, "sandboxMode");
    }

    public static CodexModelProviderConfiguration workspaceWrite(Path executable) {
        return new CodexModelProviderConfiguration(executable, CodexSandboxMode.WORKSPACE_WRITE);
    }
}
