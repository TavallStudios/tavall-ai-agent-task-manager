package com.agenttaskmanager.app.persistence.qdrant;

public interface EmbeddingProvider {

  String providerId();

  boolean isConfigured();

  EmbeddingVectorResult embedDocument(String title, String text);

  EmbeddingVectorResult embedQuery(String text);
}
