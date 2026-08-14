package org.tavall.ai.runtime;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavallAIRuntimeMainTest {
    @Test
    void runtimeIdentityNormalizesCliValues() {
        assertEquals(TavallAIRuntime.NODE_AGENT, TavallAIRuntime.parse("node-agent"));
        assertEquals(TavallAIRuntime.CHATGPT_WEB, TavallAIRuntime.parse("chatgpt-web"));
        assertEquals(TavallAIRuntime.NODE_AGENT, TavallAIRuntime.parse(null));
        assertThrows(IllegalArgumentException.class, () -> TavallAIRuntime.parse("scheduler"));
    }

    @Test
    void nodeDescribeProvesInstalledRolesAndModulesWithoutStartingAHostTransport() throws Exception {
        String description = describe("NODE_AGENT");

        assertTrue(description.contains("runtime=NODE_AGENT"));
        assertTrue(description.contains("roles=8"));
        assertTrue(description.contains("modules=2"));
        assertTrue(description.contains("module=builder"));
        assertTrue(description.contains("module=distributed-execution"));
    }

    @Test
    void chatGptWebDescribeUsesTheSameModuleUniverseWithoutStartingAWebSession() throws Exception {
        String description = describe("CHATGPT_WEB");

        assertTrue(description.contains("runtime=CHATGPT_WEB"));
        assertTrue(description.contains("roles=8"));
        assertTrue(description.contains("modules=2"));
        assertTrue(description.contains("module=builder"));
        assertTrue(description.contains("module=distributed-execution"));
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
