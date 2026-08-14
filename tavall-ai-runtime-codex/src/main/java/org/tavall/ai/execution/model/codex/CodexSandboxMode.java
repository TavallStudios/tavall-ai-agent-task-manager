package org.tavall.ai.execution.model.codex;

public enum CodexSandboxMode {
    READ_ONLY("read-only"),
    WORKSPACE_WRITE("workspace-write");

    private final String cliValue;

    CodexSandboxMode(String cliValue) {
        this.cliValue = cliValue;
    }

    public String cliValue() {
        return cliValue;
    }
}
