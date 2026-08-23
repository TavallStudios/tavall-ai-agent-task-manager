package org.tavall.agent.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agent.intelligence.FileTavallProductIntelligenceStore;
import org.tavall.agent.intelligence.TavallProductIntelligenceDisposition;
import org.tavall.agent.intelligence.TavallProductIntelligenceEntry;
import org.tavall.agent.intelligence.TavallProductIntelligenceStore;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebDesignIntelligenceServiceTest {
    @Test
    void comparisonRequiresAtLeastTwoDistinctCandidates() {
        WebDesignCandidate candidate = candidate("dense", "Dense product-first");

        assertThrows(IllegalArgumentException.class, () -> new WebDesignComparison(
                "home-direction",
                "project-novus",
                "Choose the home-page information density.",
                List.of(candidate)
        ));
        assertThrows(IllegalArgumentException.class, () -> new WebDesignComparison(
                "home-direction",
                "project-novus",
                "Choose the home-page information density.",
                List.of(candidate, candidate("dense", "Duplicate id"))
        ));
    }

    @Test
    void decisionMustSelectCandidateFromItsComparison() {
        WebDesignComparison comparison = comparison("project-novus");
        WebDesignDecision unknown = new WebDesignDecision(
                comparison.comparisonId(),
                "nonexistent",
                "Nope.",
                Instant.parse("2026-08-14T20:10:00Z")
        );

        assertThrows(IllegalArgumentException.class, () -> comparison.requireSelectedCandidate(unknown));
    }

    @Test
    void recordsGeneralDesignKnowledgeAndKeepsProductsIsolated(@TempDir Path root) throws Exception {
        WebDesignIntelligenceService service = new WebDesignIntelligenceService(new FileTavallProductIntelligenceStore(root));
        service.recordKnowledge(
                "novus-density",
                "project-novus",
                WebDesignIntelligenceCategory.VISUAL_PRINCIPLE,
                "information-density",
                "Prefer dense, product-first layouts over oversized marketing whitespace.",
                "Accepted visual direction for the Novus product surface.",
                TavallProductIntelligenceDisposition.ACCEPTED,
                Set.of("screenshot://novus/home"),
                Instant.parse("2026-08-14T20:00:00Z")
        );

        List<TavallProductIntelligenceEntry> novus = service.loadContext("project-novus");
        assertEquals(1, novus.size());
        assertEquals(WebDesignIntelligenceCategory.VISUAL_PRINCIPLE.storageKey(), novus.getFirst().category());
        assertEquals(List.of(), service.loadContext("tavall-pvp"));
    }

    @Test
    void abDecisionPersistsWinnerAndRejectedAlternatives(@TempDir Path root) throws Exception {
        WebDesignIntelligenceService service = new WebDesignIntelligenceService(new FileTavallProductIntelligenceStore(root));
        WebDesignComparison comparison = comparison("project-novus");
        WebDesignDecision decision = new WebDesignDecision(
                comparison.comparisonId(),
                "dense",
                "Dense won because it exposes live project state without turning the home page into a billboard.",
                Instant.parse("2026-08-14T20:15:00Z")
        );

        service.recordDecision(comparison, decision);

        List<TavallProductIntelligenceEntry> entries = new WebDesignIntelligenceService(
                new FileTavallProductIntelligenceStore(root)
        ).loadContext("project-novus");
        assertEquals(2, entries.size());

        TavallProductIntelligenceEntry accepted = entries.stream()
                .filter(entry -> entry.disposition() == TavallProductIntelligenceDisposition.ACCEPTED)
                .findFirst()
                .orElseThrow();
        TavallProductIntelligenceEntry rejected = entries.stream()
                .filter(entry -> entry.disposition() == TavallProductIntelligenceDisposition.REJECTED)
                .findFirst()
                .orElseThrow();

        assertTrue(accepted.entryId().startsWith("decision-"));
        assertEquals("home-direction/dense", accepted.key());
        assertEquals("Dense product-first", accepted.value());
        assertTrue(accepted.rationale().contains(decision.rationale()));
        assertTrue(rejected.entryId().startsWith("decision-"));
        assertEquals("home-direction/cinematic", rejected.key());
        assertEquals("Cinematic marketing", rejected.value());
    }

    @Test
    void dottedComparisonAndCandidatePairsCannotCollide(@TempDir Path root) throws Exception {
        WebDesignIntelligenceService service = new WebDesignIntelligenceService(new FileTavallProductIntelligenceStore(root));
        WebDesignComparison first = new WebDesignComparison(
                "a.b",
                "project-novus",
                "First dotted identity.",
                List.of(candidate("c", "First winner"), candidate("other", "First alternate"))
        );
        WebDesignComparison second = new WebDesignComparison(
                "a",
                "project-novus",
                "Second dotted identity.",
                List.of(candidate("b.c", "Second winner"), candidate("other", "Second alternate"))
        );

        service.recordDecision(first, new WebDesignDecision("a.b", "c", "first", Instant.parse("2026-08-14T20:15:00Z")));
        service.recordDecision(second, new WebDesignDecision("a", "b.c", "second", Instant.parse("2026-08-14T20:16:00Z")));

        List<TavallProductIntelligenceEntry> entries = service.loadContext("project-novus");
        TavallProductIntelligenceEntry firstWinner = entries.stream().filter(entry -> entry.key().equals("a.b/c")).findFirst().orElseThrow();
        TavallProductIntelligenceEntry secondWinner = entries.stream().filter(entry -> entry.key().equals("a/b.c")).findFirst().orElseThrow();
        assertEquals(4, entries.size());
        assertNotEquals(firstWinner.entryId(), secondWinner.entryId());
    }

    @Test
    void recordDecisionUsesOneAtomicStoreBatch() throws Exception {
        class CapturingStore implements TavallProductIntelligenceStore {
            int batchCalls;
            List<TavallProductIntelligenceEntry> captured = List.of();

            @Override
            public void recordBatch(List<TavallProductIntelligenceEntry> entries) {
                batchCalls++;
                captured = List.copyOf(entries);
            }

            @Override
            public List<TavallProductIntelligenceEntry> load(String productId, String agentId) throws IOException {
                return List.of();
            }
        }

        CapturingStore store = new CapturingStore();
        WebDesignIntelligenceService service = new WebDesignIntelligenceService(store);
        WebDesignComparison comparison = comparison("project-novus");

        service.recordDecision(comparison, new WebDesignDecision(
                comparison.comparisonId(),
                "dense",
                "one decision",
                Instant.parse("2026-08-14T20:15:00Z")
        ));

        assertEquals(1, store.batchCalls);
        assertEquals(2, store.captured.size());
    }

    private WebDesignComparison comparison(String productId) {
        return new WebDesignComparison(
                "home-direction",
                productId,
                "Choose the home-page information density.",
                List.of(
                        candidate("dense", "Dense product-first"),
                        candidate("cinematic", "Cinematic marketing")
                )
        );
    }

    private WebDesignCandidate candidate(String id, String label) {
        return new WebDesignCandidate(
                id,
                label,
                "A deliberately distinct visual direction.",
                Set.of("screenshot://" + id)
        );
    }
}
