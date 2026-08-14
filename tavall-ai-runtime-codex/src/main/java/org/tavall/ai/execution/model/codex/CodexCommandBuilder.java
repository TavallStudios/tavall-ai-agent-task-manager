package org.tavall.ai.execution.model.codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds the fixed non-interactive Codex CLI shape used by Tavall AI development runtimes. */
final class CodexCommandBuilder {
    private final CodexModelProviderConfiguration configuration;

    CodexCommandBuilder(CodexModelProviderConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    List<String> build(Path lastMessagePath) {
        Path safeLastMessagePath = Objects.requireNonNull(lastMessagePath, "lastMessagePath")
                .toAbsolutePath()
                .normalize();
        List<String> command = new ArrayList<>();
        command.add(configuration.executable().toString());
        command.add("-c");
        command.add("approval_policy=\"never\"");
        command.add("exec");
        command.add("--sandbox");
        command.add(configuration.sandboxMode().cliValue());
        command.add("--ephemeral");
        command.add("--ignore-user-config");
        command.add("--json");
        command.add("--color");
        command.add("never");
        command.add("--output-last-message");
        command.add(safeLastMessagePath.toString());
        command.add("-");
        return List.copyOf(command);
    }
}
