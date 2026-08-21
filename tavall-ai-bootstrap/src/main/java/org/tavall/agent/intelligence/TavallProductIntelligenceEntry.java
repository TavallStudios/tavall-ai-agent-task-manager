package org.tavall.agent.intelligence;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One durable, product-scoped fact or decision learned by a Tavall agent.
 *
 * <p>The entry is storage-neutral. A concrete store owns persistence and the runtime/host owns any
 * filesystem or external-service authority used by that store.</p>
 */
public record TavallProductIntelligenceEntry(
        String entryId,
        String productId,
        String agentId,
        String category,
        String key,
        String value,
        String rationale,
        TavallProductIntelligenceDisposition disposition,
        Set<String> evidenceReferences,
        Instant recordedAt
) {
    public TavallProductIntelligenceEntry {
        entryId = requireText(entryId, "entryId");
        productId = requireText(productId, "productId");
        agentId = requireText(agentId, "agentId");
        category = requireText(category, "category");
        key = requireText(key, "key");
        value = requireText(value, "value");
        rationale = requireText(rationale, "rationale");
        disposition = Objects.requireNonNull(disposition, "disposition");
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");

        LinkedHashSet<String> evidence = new LinkedHashSet<>();
        if (evidenceReferences != null) {
            for (String reference : evidenceReferences) {
                evidence.add(requireText(reference, "evidenceReferences entry"));
            }
        }
        evidenceReferences = Collections.unmodifiableSet(evidence);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
