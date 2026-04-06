package org.tavall.ai.app.persistence.qdrant;

public interface EmbeddingProvider {

  String providerId();

  boolean isConfigured();

  EmbeddingVectorResult embed(String title, String text, EmbeddingPurpose purpose);

  default EmbeddingVectorResult embedDocument(String title, String text) {
    return embed(title, text, EmbeddingPurpose.RETRIEVAL_DOCUMENT);
  }

  default EmbeddingVectorResult embedQuery(String text) {
    return embed(null, text, EmbeddingPurpose.RETRIEVAL_QUERY);
  }

  default EmbeddingVectorResult embedCodeQuery(String text) {
    return embed(null, text, EmbeddingPurpose.CODE_RETRIEVAL_QUERY);
  }
}

