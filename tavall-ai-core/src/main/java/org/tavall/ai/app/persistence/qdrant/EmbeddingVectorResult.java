package org.tavall.ai.app.persistence.qdrant;

import java.util.List;

public record EmbeddingVectorResult(String providerId, String modelName, List<Double> vector) {
}

