package org.tavall.ai.execution.model.codex;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Host-owned process-isolation boundary for Codex execution.
 *
 * <p>Tavall AI owns the Codex provider adapter, but not ambient process authority. A Tavall Cloud
 * DEVELOPMENT worker or another explicitly authorized runtime host supplies this supervisor and
 * owns execution identity, process group/cgroup lifetime, private process visibility, environment
 * isolation, cancellation, and bounded stream capture.</p>
 */
@FunctionalInterface
public interface CodexProcessIsolationSupervisor {
    CodexSupervisedProcessResult execute(CodexSupervisedProcessRequest request) throws Exception;

    record CodexSupervisedProcessRequest(
            List<String> command,
            Path workspace,
            Path standardInput,
            Map<String, String> environment,
            int maximumCaptureBytes
    ) {
        public CodexSupervisedProcessRequest {
            command = List.copyOf(Objects.requireNonNull(command, "command"));
            workspace = Objects.requireNonNull(workspace, "workspace");
            standardInput = Objects.requireNonNull(standardInput, "standardInput");
            environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
            if (command.isEmpty()) throw new IllegalArgumentException("command must not be empty");
            if (!workspace.isAbsolute()) throw new IllegalArgumentException("workspace must be absolute");
            if (!standardInput.isAbsolute()) throw new IllegalArgumentException("standardInput must be absolute");
            if (maximumCaptureBytes <= 0) throw new IllegalArgumentException("maximumCaptureBytes must be positive");
        }
    }

    record CodexSupervisedProcessResult(int exitCode, String stdout, String stderr) {
        public CodexSupervisedProcessResult {
            stdout = Objects.requireNonNull(stdout, "stdout");
            stderr = Objects.requireNonNull(stderr, "stderr");
        }
    }
}
