package org.tavall.agent.intelligence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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
        assertEquals(List.of(entry), reopened.load("project/novus web", "web"));
        assertEquals(List.of(), reopened.load("tavall-pvp", "web"));
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

        assertEquals(List.of(entry), store.load("project-novus", "web"));
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
    void recordsBatchAsOneVisibleSnapshot(@TempDir Path root) throws Exception {
        FileTavallProductIntelligenceStore store = new FileTavallProductIntelligenceStore(root);
        TavallProductIntelligenceEntry accepted = entry("decision-a", "project-novus", "web", TavallProductIntelligenceDisposition.ACCEPTED);
        TavallProductIntelligenceEntry rejected = entry("decision-b", "project-novus", "web", TavallProductIntelligenceDisposition.REJECTED);

        store.recordBatch(List.of(accepted, rejected));

        assertEquals(List.of(accepted, rejected), store.load("project-novus", "web"));
    }

    @Test
    void failedBatchLeavesPreviousSnapshotVisible(@TempDir Path root) throws Exception {
        TavallProductIntelligenceEntry existing = entry("existing", "project-novus", "web", TavallProductIntelligenceDisposition.REFERENCE);
        new FileTavallProductIntelligenceStore(root).record(existing);

        FileTavallProductIntelligenceStore interrupted = new FileTavallProductIntelligenceStore(
                root,
                (directory, target, properties) -> {
                    Files.writeString(directory.resolve("interrupted-write.tmp"), properties.toString());
                    throw new IOException("injected interruption before atomic commit");
                }
        );

        assertThrows(IOException.class, () -> interrupted.recordBatch(List.of(
                entry("decision-a", "project-novus", "web", TavallProductIntelligenceDisposition.ACCEPTED),
                entry("decision-b", "project-novus", "web", TavallProductIntelligenceDisposition.REJECTED)
        )));

        assertEquals(
                List.of(existing),
                new FileTavallProductIntelligenceStore(root).load("project-novus", "web"),
                "a failed batch must not expose a partial replacement"
        );
    }

    @Test
    void batchRequiresOneProductAndAgentScope(@TempDir Path root) {
        FileTavallProductIntelligenceStore store = new FileTavallProductIntelligenceStore(root);

        assertThrows(IllegalArgumentException.class, () -> store.recordBatch(List.of(
                entry("one", "project-novus", "web", TavallProductIntelligenceDisposition.REFERENCE),
                entry("two", "tavall-pvp", "web", TavallProductIntelligenceDisposition.REFERENCE)
        )));
        assertThrows(IllegalArgumentException.class, () -> store.recordBatch(List.of(
                entry("one", "project-novus", "web", TavallProductIntelligenceDisposition.REFERENCE),
                entry("two", "project-novus", "builder", TavallProductIntelligenceDisposition.REFERENCE)
        )));
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

    @Test
    void rejectsDotOnlyEntryAndAgentIdentifiers(@TempDir Path root) {
        FileTavallProductIntelligenceStore store = new FileTavallProductIntelligenceStore(root);

        assertThrows(IllegalArgumentException.class, () -> store.record(entry(".", "project-novus", "web", TavallProductIntelligenceDisposition.REFERENCE)));
        assertThrows(IllegalArgumentException.class, () -> store.record(entry("safe-entry", "project-novus", ".", TavallProductIntelligenceDisposition.REFERENCE)));
        assertThrows(IllegalArgumentException.class, () -> store.record(entry("safe-entry", "project-novus", "..", TavallProductIntelligenceDisposition.REFERENCE)));
        assertThrows(IllegalArgumentException.class, () -> store.load("project-novus", "."));
        assertThrows(IllegalArgumentException.class, () -> store.load("project-novus", ".."));
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
