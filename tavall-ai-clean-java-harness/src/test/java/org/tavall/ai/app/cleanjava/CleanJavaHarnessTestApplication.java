package org.tavall.ai.app.cleanjava;

import org.tavall.ai.app.config.CodexBridgeProperties;
import org.tavall.ai.app.config.CodexClientPlatformProperties;
import org.tavall.ai.app.config.CodexExecutionProperties;
import org.tavall.ai.app.config.ComputerUseProperties;
import org.tavall.ai.app.config.EmbeddingProperties;
import org.tavall.ai.app.config.KnowledgeIndexProperties;
import org.tavall.ai.app.config.MemoryRuntimeProperties;
import org.tavall.ai.app.config.MemorySyncProperties;
import org.tavall.ai.app.config.McpServerProperties;
import org.tavall.ai.app.config.MongoProperties;
import org.tavall.ai.app.config.OperatorSurfaceProperties;
import org.tavall.ai.app.config.OrchestrationProperties;
import org.tavall.ai.app.config.QdrantProperties;
import org.tavall.ai.app.config.RepoCatalogProperties;
import org.tavall.ai.app.config.SecurityProperties;
import org.tavall.ai.app.config.SemanticIndexProperties;
import org.tavall.ai.app.config.TaskRuntimeProperties;
import org.tavall.ai.app.config.ToolPolicyProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    scanBasePackages = {"org.tavall.ai.app", "cache"},
    excludeName = {
        "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration",
        "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration"
    }
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
    MemorySyncProperties.class,
    MemoryRuntimeProperties.class,
    OrchestrationProperties.class,
    McpServerProperties.class,
    ToolPolicyProperties.class
})
public class CleanJavaHarnessTestApplication {
}

