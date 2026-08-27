package org.tavall.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Loads canonical instructions packaged by a Tavall agent artifact. */
public final class TavallAgentInstructions {
    private TavallAgentInstructions() {
    }

    public static String load(Class<?> owner, String resourceName) {
        Class<?> safeOwner = Objects.requireNonNull(owner, "owner");
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("resourceName must not be blank");
        }
        try (InputStream stream = safeOwner.getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Missing Tavall agent instructions " + resourceName + " for " + safeOwner.getName()
                );
            }
            String instructions = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (instructions.isBlank()) {
                throw new IllegalStateException("Tavall agent instructions must not be blank: " + resourceName);
            }
            return instructions;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read Tavall agent instructions: " + resourceName, exception);
        }
    }
}
