package com.agenttaskmanager.app;

import com.agenttaskmanager.app.config.CodexBridgeProperties;
import com.agenttaskmanager.app.config.CodexExecutionProperties;
import com.agenttaskmanager.app.config.EmbeddingProperties;
import com.agenttaskmanager.app.config.KnowledgeIndexProperties;
import com.agenttaskmanager.app.config.McpServerProperties;
import com.agenttaskmanager.app.config.MongoProperties;
import com.agenttaskmanager.app.config.OperatorSurfaceProperties;
import com.agenttaskmanager.app.config.OrchestrationProperties;
import com.agenttaskmanager.app.config.QdrantProperties;
import com.agenttaskmanager.app.config.RepoCatalogProperties;
import com.agenttaskmanager.app.config.SecurityProperties;
import com.agenttaskmanager.app.config.TaskRuntimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.agenttaskmanager.app", "cache"})
@EnableScheduling
@EnableConfigurationProperties({
    SecurityProperties.class,
    TaskRuntimeProperties.class,
    CodexBridgeProperties.class,
    CodexExecutionProperties.class,
    RepoCatalogProperties.class,
    OperatorSurfaceProperties.class,
    MongoProperties.class,
    QdrantProperties.class,
    EmbeddingProperties.class,
    KnowledgeIndexProperties.class,
    OrchestrationProperties.class,
    McpServerProperties.class
})
public class AgentTaskManagerApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgentTaskManagerApplication.class, args);
  }
}
