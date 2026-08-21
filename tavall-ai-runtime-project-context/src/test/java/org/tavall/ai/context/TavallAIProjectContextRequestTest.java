package org.tavall.ai.context;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TavallAIProjectContextRequestTest {
    @Test
    void preservesExplicitlyEmptyAuthorizedKinds() {
        TavallAIProjectContextRequest request = new TavallAIProjectContextRequest(
                "chatgpt-project",
                "project-novus",
                "current architecture",
                Set.of(),
                8,
                4_096
        );

        assertEquals(Set.of(), request.kinds());
    }

    @Test
    void nullKindsRetainDefaultAllKindsBehavior() {
        TavallAIProjectContextRequest request = new TavallAIProjectContextRequest(
                "chatgpt-project",
                "project-novus",
                "current architecture",
                null,
                8,
                4_096
        );

        assertEquals(Set.of(TavallAIContextKind.values()), request.kinds());
    }
}
