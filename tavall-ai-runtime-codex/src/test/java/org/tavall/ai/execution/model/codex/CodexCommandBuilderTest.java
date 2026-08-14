package org.tavall.ai.execution.model.codex;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexCommandBuilderTest {
    @Test
    void buildsFixedEphemeralWorkspaceWriteExecutionWithoutDangerousBypass() {
        CodexCommandBuilder builder = new CodexCommandBuilder(
                CodexModelProviderConfiguration.workspaceWrite(Path.of("/usr/local/bin/codex"))
        );

        List<String> command = builder.build(Path.of("/tmp/last-message.txt"));

        assertEquals(List.of(
                "/usr/local/bin/codex",
                "-c",
                "approval_policy=\"never\"",
                "exec",
                "--sandbox",
                "workspace-write",
                "--ephemeral",
                "--ignore-user-config",
                "--json",
                "--color",
                "never",
                "--output-last-message",
                "/tmp/last-message.txt",
                "-"
        ), command);
        assertFalse(command.stream().anyMatch(argument -> argument.contains("dangerously-bypass")));
        assertFalse(command.contains("--full-auto"));
    }

    @Test
    void supportsReadOnlyDelegatedWorkers() {
        CodexCommandBuilder builder = new CodexCommandBuilder(
                new CodexModelProviderConfiguration(
                        Path.of("/usr/local/bin/codex"),
                        CodexSandboxMode.READ_ONLY
                )
        );

        List<String> command = builder.build(Path.of("/tmp/result"));
        int sandbox = command.indexOf("--sandbox");
        assertTrue(sandbox >= 0);
        assertEquals("read-only", command.get(sandbox + 1));
    }
}
