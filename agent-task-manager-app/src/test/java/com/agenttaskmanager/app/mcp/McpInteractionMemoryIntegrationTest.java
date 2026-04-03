package com.agenttaskmanager.app.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.model.PromptThreadDetail;
import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import com.agenttaskmanager.app.persistence.postgres.PromptThreadRepository;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class McpInteractionMemoryIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private JdbcClient jdbcClient;

  @Autowired
  private McpCatalog mcpCatalog;

  @Autowired
  private PromptThreadRepository promptThreadRepository;

  @Autowired
  private SharedTaskContextService sharedTaskContextService;

  @BeforeEach
  void cleanup() {
    jdbcClient.sql("DELETE FROM agent_task_manager.prompt_messages").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.prompt_runs").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.prompt_threads").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.prompt_requests").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.shared_task_context").update();
  }

  @Test
  void shouldPersistThreadHistoryAndSemanticMemoryForWrappedTools() {
    SyncToolSpecification specification = mcpCatalog.toolSpecifications().stream()
        .filter(item -> "loadDashboardSummary".equals(item.tool().name()))
        .findFirst()
        .orElseThrow();

    specification.callHandler().apply(
        null,
        CallToolRequest.builder()
            .name("loadDashboardSummary")
            .arguments(Map.of())
            .meta(Map.of(
                "threadKey", "mcp-tool-thread",
                "projectKey", "integration-mcp-memory-tool",
                "repoPath", "/srv/AgentTaskManager"
            ))
            .build()
    );

    PromptThreadDetail detail = promptThreadRepository.getDetail("mcp-tool-thread");
    assertTrue(detail.messages().stream().anyMatch(message -> "mcp-tool-request".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "mcp-memory-lookup".equals(message.messageKind())));
    assertTrue(detail.messages().stream().anyMatch(message -> "mcp-tool-result".equals(message.messageKind())));
    assertFalse(
        sharedTaskContextService.searchProjectRelatedContexts(
            "integration-mcp-memory-tool",
            "loadDashboardSummary",
            10,
            Map.of("threadKey", "mcp-tool-thread")
        ).isEmpty()
    );
  }

  @Test
  void shouldPersistPromptAndResourceInteractions() {
    SyncPromptSpecification promptSpecification = mcpCatalog.promptSpecifications().stream()
        .filter(item -> "workerAgent".equals(item.prompt().name()))
        .findFirst()
        .orElseThrow();
    promptSpecification.promptHandler().apply(
        null,
        new GetPromptRequest(
            "workerAgent",
            Map.of("taskId", "task-123"),
            Map.of("threadKey", "mcp-prompt-thread", "projectKey", "integration-mcp-memory-prompt")
        )
    );

    SyncResourceSpecification resourceSpecification = mcpCatalog.resourceSpecifications().stream()
        .filter(item -> "state://dashboard/summary".equals(item.resource().uri()))
        .findFirst()
        .orElseThrow();
    resourceSpecification.readHandler().apply(
        null,
        new ReadResourceRequest(
            "state://dashboard/summary",
            Map.of("threadKey", "mcp-resource-thread", "projectKey", "integration-mcp-memory-resource")
        )
    );

    PromptThreadDetail promptDetail = promptThreadRepository.getDetail("mcp-prompt-thread");
    PromptThreadDetail resourceDetail = promptThreadRepository.getDetail("mcp-resource-thread");
    assertTrue(promptDetail.messages().stream().anyMatch(message -> "mcp-prompt-result".equals(message.messageKind())));
    assertTrue(resourceDetail.messages().stream().anyMatch(message -> "mcp-resource-result".equals(message.messageKind())));
    assertFalse(
        sharedTaskContextService.searchProjectRelatedContexts(
            "integration-mcp-memory-prompt",
            "workerAgent",
            10,
            Map.of("threadKey", "mcp-prompt-thread")
        ).isEmpty()
    );
    assertFalse(
        sharedTaskContextService.searchProjectRelatedContexts(
            "integration-mcp-memory-resource",
            "state://dashboard/summary",
            10,
            Map.of("threadKey", "mcp-resource-thread")
        ).isEmpty()
    );
  }
}
