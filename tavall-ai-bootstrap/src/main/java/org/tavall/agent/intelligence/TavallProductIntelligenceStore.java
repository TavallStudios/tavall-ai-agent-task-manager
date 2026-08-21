package org.tavall.agent.intelligence;

import java.io.IOException;
import java.util.List;

/** Durable storage boundary for product-scoped agent intelligence. */
public interface TavallProductIntelligenceStore {
    void record(TavallProductIntelligenceEntry entry) throws IOException;

    /**
     * Records one logically atomic group of intelligence entries.
     *
     * <p>Implementations must make either the complete group or none of the group visible to
     * readers. This boundary is used for multi-entry decisions whose accepted and rejected
     * alternatives must never become partially durable.</p>
     */
    void recordAll(List<TavallProductIntelligenceEntry> entries) throws IOException;

    List<TavallProductIntelligenceEntry> load(String productId, String agentId) throws IOException;
}
