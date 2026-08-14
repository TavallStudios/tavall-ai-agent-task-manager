package org.tavall.ai.execution.model.codex;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodexModelProviderContractTest {
    @Test
    void inheritedEnvironmentIsExplicitlyAllowlisted() {
        Map<String, String> sanitized = CodexModelProvider.sanitizedEnvironment(Map.of(
                "PATH", "/usr/bin",
                "OPENAI_API_KEY", "test-key",
                "TAVALL_CLOUD_CONTROL_HMAC", "sentinel-control-value",
                "DATABASE_PASSWORD", "sentinel-database-value"
        ));

        assertEquals("/usr/bin", sanitized.get("PATH"));
        assertEquals("test-key", sanitized.get("OPENAI_API_KEY"));
        assertFalse(sanitized.containsKey("TAVALL_CLOUD_CONTROL_HMAC"));
        assertFalse(sanitized.containsKey("DATABASE_PASSWORD"));
    }

    @Test
    void supervisorRequestRequiresHostOwnedAbsoluteWorkspaceAndInput() {
        assertThrows(IllegalArgumentException.class, () ->
                new CodexProcessIsolationSupervisor.CodexSupervisedProcessRequest(
                        java.util.List.of("codex"),
                        Path.of("relative-workspace"),
                        Path.of("/tmp/prompt"),
                        Map.of(),
                        1024
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new CodexProcessIsolationSupervisor.CodexSupervisedProcessRequest(
                        java.util.List.of("codex"),
                        Path.of("/tmp/workspace"),
                        Path.of("relative-prompt"),
                        Map.of(),
                        1024
                )
        );
    }
}
