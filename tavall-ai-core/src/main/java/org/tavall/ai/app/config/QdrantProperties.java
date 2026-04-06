package org.tavall.ai.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.qdrant")
public class QdrantProperties {

  private String baseUrl = "";
  private String collection = "agent_task_manager_context_v2";
  private String projectCollectionPrefix = "agent_task_manager_project";
  private String knowledgeCollectionPrefix = "agent_task_manager_knowledge";

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getCollection() {
    return collection;
  }

  public void setCollection(String collection) {
    this.collection = collection;
  }

  public String getProjectCollectionPrefix() {
    return projectCollectionPrefix;
  }

  public void setProjectCollectionPrefix(String projectCollectionPrefix) {
    this.projectCollectionPrefix = projectCollectionPrefix;
  }

  public String getKnowledgeCollectionPrefix() {
    return knowledgeCollectionPrefix;
  }

  public void setKnowledgeCollectionPrefix(String knowledgeCollectionPrefix) {
    this.knowledgeCollectionPrefix = knowledgeCollectionPrefix;
  }
}

