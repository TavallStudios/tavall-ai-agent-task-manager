package org.tavall.ai.app.persistence.qdrant;

import org.tavall.ai.app.config.EmbeddingProperties;
import org.tavall.ai.app.config.QdrantProperties;
import org.tavall.ai.app.retrieval.SemanticCollectionDomain;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class QdrantCollectionNameResolver {

  private final QdrantProperties properties;
  private final EmbeddingProperties embeddingProperties;

  public QdrantCollectionNameResolver(QdrantProperties properties, EmbeddingProperties embeddingProperties) {
    this.properties = properties;
    this.embeddingProperties = embeddingProperties;
  }

  public String legacyCollection() {
    return properties.getCollection();
  }

  public String projectCollection(String projectKey) {
    return profiled(properties.getProjectCollectionPrefix() + "_" + sanitize(projectKey));
  }

  public String projectCollection(String projectKey, SemanticCollectionDomain domain) {
    return profiled(properties.getProjectCollectionPrefix() + "_" + sanitize(projectKey) + "_" + domain.collectionSuffix());
  }

  public String knowledgeCollection(String knowledgeBase) {
    return profiled(properties.getKnowledgeCollectionPrefix() + "_" + sanitize(knowledgeBase));
  }

  public String knowledgeCollection(String knowledgeBase, SemanticCollectionDomain domain) {
    return profiled(properties.getKnowledgeCollectionPrefix() + "_" + sanitize(knowledgeBase) + "_" + domain.collectionSuffix());
  }

  public String embeddingProfile() {
    return embeddingProperties.collectionProfile();
  }

  private String profiled(String logicalCollectionName) {
    return logicalCollectionName + "__" + embeddingProfile();
  }

  private static String sanitize(String rawValue) {
    String normalized = rawValue == null ? "" : rawValue.strip().toLowerCase(Locale.ROOT);
    normalized = normalized.replaceAll("[^a-z0-9]+", "_");
    normalized = normalized.replaceAll("^_+|_+$", "");
    return normalized.isEmpty() ? "default" : normalized;
  }
}
