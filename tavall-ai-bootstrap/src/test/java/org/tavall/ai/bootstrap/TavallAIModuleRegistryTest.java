package org.tavall.ai.bootstrap;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TavallAIModuleRegistryTest {
    @Test
    void composesModulesAndValidatesDependencies() {
        TavallAIModuleRegistry registry = TavallAIModuleRegistry.of(List.of(
                provider(new TavallAIModule("distributed-execution", "routes AI calls", Set.of())),
                provider(new TavallAIModule("builder", "Builder domain module", Set.of("distributed-execution")))
        ));

        assertEquals(2, registry.size());
        assertEquals("builder", registry.require("builder").id());
    }

    @Test
    void rejectsMissingRequiredModule() {
        assertThrows(IllegalStateException.class, () -> TavallAIModuleRegistry.of(List.of(
                provider(new TavallAIModule("builder", "Builder domain module", Set.of("distributed-execution")))
        )));
    }

    @Test
    void rejectsDuplicateModuleIds() {
        assertThrows(IllegalArgumentException.class, () -> TavallAIModuleRegistry.of(List.of(
                provider(new TavallAIModule("builder", "one", Set.of())),
                provider(new TavallAIModule("builder", "two", Set.of()))
        )));
    }

    private TavallAIModuleProvider provider(TavallAIModule module) {
        return () -> module;
    }
}
