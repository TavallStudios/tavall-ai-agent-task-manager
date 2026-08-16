package org.tavall.ai.runtime;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftAgentFamilyRuntimeTest {
    @Test
    void runtimePublishesMinecraftCoordinatorAndSpecialists() throws Exception {
        String description = describe("NODE_AGENT");

        assertTrue(description.contains("agents=13"), description);
        for (String agentId : Set.of(
                "minecraft",
                "builder",
                "minecraft-observer",
                "minecraft-traversal-validator",
                "minecraft-gameplay-validator"
        )) {
            assertTrue(description.contains("agent=" + agentId), description);
        }
    }

    @Test
    void chatGptWebSeesTheSameMinecraftFamily() throws Exception {
        String description = describe("CHATGPT_WEB");

        assertTrue(description.contains("agents=13"), description);
        assertTrue(description.contains("agent=minecraft"), description);
        assertTrue(description.contains("agent=builder"), description);
        assertTrue(description.contains("agent=minecraft-observer"), description);
        assertTrue(description.contains("agent=minecraft-traversal-validator"), description);
        assertTrue(description.contains("agent=minecraft-gameplay-validator"), description);
    }

    private String describe(String runtime) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            int exitCode = TavallAIRuntimeMain.run(
                    new String[] {runtime, "--describe"},
                    Map.of(),
                    output,
                    Thread.currentThread().getContextClassLoader()
            );
            assertEquals(0, exitCode);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
