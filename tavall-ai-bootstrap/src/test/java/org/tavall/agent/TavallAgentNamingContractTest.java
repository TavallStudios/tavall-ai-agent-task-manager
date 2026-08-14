package org.tavall.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavallAgentNamingContractTest {
    @Test
    void activeGradleProjectsKeepAgentsNonAiAndRuntimeCapabilitiesRuntimeOwned() throws Exception {
        Path settings = locateRepositoryRoot().resolve("settings.gradle.kts");
        String text = Files.readString(settings);

        assertFalse(text.contains("\"tavall-ai-agent-"), "Active agent projects must not use the tavall-ai-agent-* prefix");
        assertFalse(text.contains("\"tavall-ai-agent-core\""), "Agent contracts belong in tavall-ai-bootstrap");
        assertTrue(text.contains("\"tavall-agent-scheduler\""));
        assertTrue(text.contains("\"tavall-agent-builder\""));
        assertTrue(text.contains("\"tavall-ai-runtime-distributed-execution\""));
    }

    @Test
    void agentContractPackageDoesNotExposeAiRuntimeIdentity() {
        List<String> typeNames = List.of(
                TavallAgent.class.getSimpleName(),
                TavallAgentProvider.class.getSimpleName(),
                TavallAgentRegistry.class.getSimpleName(),
                TavallAgentKind.class.getSimpleName(),
                TavallAgentCapability.class.getSimpleName()
        );

        assertTrue(typeNames.stream().allMatch(name -> !name.contains("AI")));
    }

    private Path locateRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Could not locate Tavall AI repository root");
        }
        return current;
    }
}
