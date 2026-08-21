package org.tavall.agent.web;

import org.tavall.agent.intelligence.TavallProductIntelligenceDisposition;
import org.tavall.agent.intelligence.TavallProductIntelligenceEntry;
import org.tavall.agent.intelligence.TavallProductIntelligenceStore;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Web-specific adapter over the reusable Tavall product-intelligence store. */
public final class WebDesignIntelligenceService {
    public static final String AGENT_ID = "web";

    private final TavallProductIntelligenceStore store;

    public WebDesignIntelligenceService(TavallProductIntelligenceStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public void recordKnowledge(
            String entryId,
            String productId,
            WebDesignIntelligenceCategory category,
            String key,
            String value,
            String rationale,
            TavallProductIntelligenceDisposition disposition,
            Set<String> evidenceReferences,
            Instant recordedAt
    ) throws IOException {
        store.record(new TavallProductIntelligenceEntry(
                entryId,
                productId,
                AGENT_ID,
                Objects.requireNonNull(category, "category").storageKey(),
                key,
                value,
                rationale,
                Objects.requireNonNull(disposition, "disposition"),
                evidenceReferences,
                recordedAt
        ));
    }

    public List<TavallProductIntelligenceEntry> loadContext(String productId) throws IOException {
        return store.load(productId, AGENT_ID);
    }

    public void recordDecision(WebDesignComparison comparison, WebDesignDecision decision) throws IOException {
        Objects.requireNonNull(comparison, "comparison");
        Objects.requireNonNull(decision, "decision");
        WebDesignCandidate selected = comparison.requireSelectedCandidate(decision);

        for (WebDesignCandidate candidate : comparison.candidates()) {
            TavallProductIntelligenceDisposition disposition = candidate.id().equals(selected.id())
                    ? TavallProductIntelligenceDisposition.ACCEPTED
                    : TavallProductIntelligenceDisposition.REJECTED;
            String rationale = "Comparison brief: " + comparison.brief()
                    + "\nCandidate rationale: " + candidate.rationale()
                    + "\nDecision rationale: " + decision.rationale();

            recordKnowledge(
                    WebDesignDecisionEntryId.from(comparison.comparisonId(), candidate.id()).value(),
                    comparison.productId(),
                    WebDesignIntelligenceCategory.DESIGN_DECISION,
                    comparison.comparisonId() + "/" + candidate.id(),
                    candidate.label(),
                    rationale,
                    disposition,
                    candidate.evidenceReferences(),
                    decision.decidedAt()
            );
        }
    }
}
