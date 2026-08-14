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
    void describeProvesInstalledRolesWithoutStartingAHostTransport() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            int exitCode = TavallAIRuntimeMain.run(
                    new String[] {"NODE_AGENT", "--describe"},
                    Map.of(),
                    output,
                    Thread.currentThread().getContextClassLoader()
            );
            assertEquals(0, exitCode);
        }
        String description = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(description.contains("runtime=NODE_AGENT"));
        assertTrue(description.contains("roles=8"));
    }

    @Test
    void chatGptWebDescribeDoesNotRequireAWebHostSession() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            int exitCode = TavallAIRuntimeMain.run(
                    new String[] {"CHATGPT_WEB", "--describe"},
                    Map.of(),
                    output,
                    Thread.currentThread().getContextClassLoader()
            );
            assertEquals(0, exitCode);
        }
        String description = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(description.contains("runtime=CHATGPT_WEB"));
        assertTrue(description.contains("roles=8"));
    }
}
