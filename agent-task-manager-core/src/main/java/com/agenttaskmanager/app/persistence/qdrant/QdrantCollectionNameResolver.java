package com.agenttaskmanager.app.persistence.qdrant;

import com.agenttaskmanager.app.config.QdrantProperties;
import com.agenttaskmanager.app.retrieval.SemanticCollectionDomain;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class QdrantCollectionNameResolver {

  private final QdrantProperties properties;

  public QdrantCollectionNameResolver(QdrantProperties properties) {
    this.properties = properties;
  }

  public String legacyCollection() {
    return properties.getCollection();
  }

  public String projectCollection(String projectKey) {
    return properties.getProjectCollectionPrefix() + "_" + sanitize(projectKey);
  }

  public String projectCollection(String projectKey, SemanticCollectionDomain domain) {
    return projectCollection(projectKey) + "_" + domain.collectionSuffix();
  }

  public String knowledgeCollection(String knowledgeBase) {
    return properties.getKnowledgeCollectionPrefix() + "_" + sanitize(knowledgeBase);
  }

  public String knowledgeCollection(String knowledgeBase, SemanticCollectionDomain domain) {
    return knowledgeCollection(knowledgeBase) + "_" + domain.collectionSuffix();
  }

  private static String sanitize(String rawValue) {
    String normalized = rawValue == null ? "" : rawValue.strip().toLowerCase(Locale.ROOT);
    normalized = normalized.replaceAll("[^a-z0-9]+", "_");
    normalized = normalized.replaceAll("^_+|_+$", "");
    return normalized.isEmpty() ? "default" : normalized;
  }
}
