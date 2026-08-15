package org.tavall.agent.intelligence;

import java.io.IOException;
import java.util.List;

/** Durable storage boundary for product-scoped agent intelligence. */
public interface TavallProductIntelligenceStore {
    void record(TavallProductIntelligenceEntry entry) throws IOException;

    List<TavallProductIntelligenceEntry> load(String productId, String agentId) throws IOException;
}
