package org.tavall.ai.app.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.qdrant")
public class QdrantProperties {

  private String baseUrl = "";
  private String apiKey = "";
  private String collection = "agent_task_manager_context_v2";
  private String projectCollectionPrefix = "agent_task_manager_project";
  private String knowledgeCollectionPrefix = "agent_task_manager_knowledge";
  private Duration requestTimeout = Duration.ofSeconds(10);

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
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

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }
}
