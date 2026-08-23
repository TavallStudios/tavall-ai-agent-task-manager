package org.tavall.agent.intelligence;

import java.io.IOException;
import java.util.List;

/** Durable storage boundary for product-scoped agent intelligence. */
public interface TavallProductIntelligenceStore {
    default void record(TavallProductIntelligenceEntry entry) throws IOException {
        recordBatch(List.of(entry));
    }

    /**
     * Atomically records one product/agent-scoped batch. Implementations must expose either the
     * complete batch or none of it if persistence fails.
     */
    void recordBatch(List<TavallProductIntelligenceEntry> entries) throws IOException;

    List<TavallProductIntelligenceEntry> load(String productId, String agentId) throws IOException;
}
