package org.tavall.ai.agent.role;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Loads the canonical role instructions packaged by a deployable role module. */
public final class TavallAIAgentRoleInstructions {
    private TavallAIAgentRoleInstructions() {
    }

    public static String load(Class<?> owner, String resourceName) {
        Class<?> safeOwner = Objects.requireNonNull(owner, "owner");
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("resourceName must not be blank");
        }
        try (InputStream stream = safeOwner.getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Missing Tavall AI role instructions " + resourceName + " for " + safeOwner.getName()
                );
            }
            String instructions = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (instructions.isBlank()) {
                throw new IllegalStateException(
                        "Tavall AI role instructions must not be blank: " + resourceName
                );
            }
            return instructions;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to read Tavall AI role instructions: " + resourceName,
                    exception
            );
        }
    }
}
