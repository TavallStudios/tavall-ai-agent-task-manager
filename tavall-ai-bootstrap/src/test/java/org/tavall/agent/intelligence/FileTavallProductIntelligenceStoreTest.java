package org.tavall.agent.intelligence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileTavallProductIntelligenceStoreTest {
    @Test
    void persistsAcrossStoreInstancesAndIsolatesProducts(@TempDir Path root) throws Exception {
        TavallProductIntelligenceEntry entry = entry(
                "accepted-home-v2",
                "project/novus web",
                "web",
                TavallProductIntelligenceDisposition.ACCEPTED
        );

        new FileTavallProductIntelligenceStore(root).record(entry);

        FileTavallProductIntelligenceStore reopened = new FileTavallProductIntelligenceStore(root);
        assertEquals(java.util.List.of(entry), reopened.load("project/novus web", "web"));
        assertEquals(java.util.List.of(), reopened.load("tavall-pvp", "web"));
        assertFalse(Files.exists(root.resolve("project/novus web")), "product ids must never become raw paths");
    }

    @Test
    void persistsShortValidEntryIdentifiers(@TempDir Path root) throws Exception {
        TavallProductIntelligenceEntry entry = entry(
                "a",
                "project-novus",
                "web",
                TavallProductIntelligenceDisposition.REFERENCE
        );
        FileTavallProductIntelligenceStore store = new FileTavallProductIntelligenceStore(root);

        store.record(entry);

        assertEquals(java.util.List.of(entry), store.load("project-novus", "web"));
    }

    @Test
    void isolatesAgentsWithinTheSameProduct(@TempDir Path root) throws Exception {
        FileTavallProductIntelligenceStore store = new FileTavallProductIntelligenceStore(root);
        store.record(entry("web-choice", "project-novus", "web", TavallProductIntelligenceDisposition.REFERENCE));
        store.record(entry("builder-choice", "project-novus", "builder", TavallProductIntelligenceDisposition.REFERENCE));

        assertEquals(1, store.load("project-novus", "web").size());
        assertEquals("web-choice", store.load("project-novus", "web").getFirst().entryId());
        assertEquals(1, store.load("project-novus", "builder").size());
        assertEquals("builder-choice", store.load("project-novus", "builder").getFirst().entryId());
    }

    @Test
    void rejectsUnsafeFilesystemIdentifiers(@TempDir Path root) {
        FileTavallProductIntelligenceStore store = new FileTavallProductIntelligenceStore(root);

        assertThrows(IllegalArgumentException.class, () -> store.record(entry(
                "../escape",
                "project-novus",
                "web",
                TavallProductIntelligenceDisposition.REFERENCE
        )));
        assertThrows(IllegalArgumentException.class, () -> store.record(entry(
                "safe-entry",
                "project-novus",
                "../web",
                TavallProductIntelligenceDisposition.REFERENCE
        )));
        assertThrows(IllegalArgumentException.class, () -> store.load("project-novus", "../web"));
    }

    private TavallProductIntelligenceEntry entry(
            String entryId,
            String productId,
            String agentId,
            TavallProductIntelligenceDisposition disposition
    ) {
        return new TavallProductIntelligenceEntry(
                entryId,
                productId,
                agentId,
                "design-decision",
                "home-page",
                "dense product-first layout",
                "Approved after live A/B comparison.",
                disposition,
                Set.of("screenshot://home-v2"),
                Instant.parse("2026-08-14T20:00:00Z")
        );
    }
}
