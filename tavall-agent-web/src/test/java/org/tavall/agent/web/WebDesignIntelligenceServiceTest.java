package org.tavall.agent.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agent.intelligence.FileTavallProductIntelligenceStore;
import org.tavall.agent.intelligence.TavallProductIntelligenceDisposition;
import org.tavall.agent.intelligence.TavallProductIntelligenceEntry;

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
        WebDesignIntelligenceService service = new WebDesignIntelligenceService(
                new FileTavallProductIntelligenceStore(root)
        );
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
        WebDesignIntelligenceService service = new WebDesignIntelligenceService(
                new FileTavallProductIntelligenceStore(root)
        );
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

        assertEquals(WebDesignDecisionEntryId.from(comparison.comparisonId(), "dense").value(), accepted.entryId());
        assertEquals("Dense product-first", accepted.value());
        assertTrue(accepted.rationale().contains(decision.rationale()));
        assertEquals(WebDesignDecisionEntryId.from(comparison.comparisonId(), "cinematic").value(), rejected.entryId());
        assertEquals("Cinematic marketing", rejected.value());
    }

    @Test
    void dottedComparisonAndCandidateIdsCannotAliasTheSameDecisionEntry() {
        WebDesignDecisionEntryId first = WebDesignDecisionEntryId.from("a.b", "c");
        WebDesignDecisionEntryId second = WebDesignDecisionEntryId.from("a", "b.c");

        assertNotEquals(first, second);
        assertNotEquals(first.value(), second.value());
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
