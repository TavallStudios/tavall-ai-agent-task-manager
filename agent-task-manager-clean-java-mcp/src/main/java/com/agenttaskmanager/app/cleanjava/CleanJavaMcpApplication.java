package com.agenttaskmanager.app.cleanjava;

import com.agenttaskmanager.app.config.CodexBridgeProperties;
import com.agenttaskmanager.app.config.CodexClientPlatformProperties;
import com.agenttaskmanager.app.config.CodexExecutionProperties;
import com.agenttaskmanager.app.config.ComputerUseProperties;
import com.agenttaskmanager.app.config.EmbeddingProperties;
import com.agenttaskmanager.app.config.KnowledgeIndexProperties;
import com.agenttaskmanager.app.config.McpServerProperties;
import com.agenttaskmanager.app.config.MongoProperties;
import com.agenttaskmanager.app.config.OperatorSurfaceProperties;
import com.agenttaskmanager.app.config.OrchestrationProperties;
import com.agenttaskmanager.app.config.QdrantProperties;
import com.agenttaskmanager.app.config.RepoCatalogProperties;
import com.agenttaskmanager.app.config.SecurityProperties;
import com.agenttaskmanager.app.config.SemanticIndexProperties;
import com.agenttaskmanager.app.config.TaskRuntimeProperties;
import com.agenttaskmanager.app.config.ToolPolicyProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    scanBasePackages = {"com.agenttaskmanager.app", "cache"},
    exclude = MongoAutoConfiguration.class
)
@EnableScheduling
@EnableConfigurationProperties({
    SecurityProperties.class,
    TaskRuntimeProperties.class,
    ComputerUseProperties.class,
    CodexBridgeProperties.class,
    CodexClientPlatformProperties.class,
    CodexExecutionProperties.class,
    RepoCatalogProperties.class,
    OperatorSurfaceProperties.class,
    MongoProperties.class,
    QdrantProperties.class,
    EmbeddingProperties.class,
    SemanticIndexProperties.class,
    KnowledgeIndexProperties.class,
    OrchestrationProperties.class,
    McpServerProperties.class,
    ToolPolicyProperties.class
})
public class CleanJavaMcpApplication {
}
