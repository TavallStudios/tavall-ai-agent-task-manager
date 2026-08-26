package org.tavall.ai.app.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.tavall.ai.app.mcp.tools.context.ContextToolHandler;
import org.tavall.ai.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ContextToolHandlerTest extends IntegrationTestSupport {

  @Autowired
  private ContextToolHandler handler;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void shouldRegisterContextTools() {
    Set<String> handlerTools = McpToolHandlerIntegrationAssertions.handlerToolNames(handler);
    Set<String> serverTools = McpToolHandlerIntegrationAssertions.serverToolNames(mcpSyncServer);

    McpToolHandlerIntegrationAssertions.assertContainsAll(
        handlerTools,
        "loadTaskContext",
        "loadArchitectureRules",
        "loadExamples",
        "loadValidationHistory",
        "loadDashboardSummary",
        "loadChatState",
        "searchSemanticContext",
        "loadSiblingTaskSummaries",
        "storeSharedTaskContext",
        "loadSharedTaskContext"
    );
    McpToolHandlerIntegrationAssertions.assertContainsAll(serverTools, "loadTaskContext", "loadDashboardSummary");
  }

  @Test
  void shouldLoadRepositoryDocsWhenTheWorkingDirectoryIsThePackagedReleaseRoot() {
    SyncToolSpecification specification = handler.toolSpecifications().stream()
        .filter(item -> "loadArchitectureRules".equals(item.tool().name()))
        .findFirst()
        .orElseThrow();

    CallToolResult result = (CallToolResult) specification.callHandler().apply(
        null,
        CallToolRequest.builder()
            .name("loadArchitectureRules")
            .arguments(Map.of())
            .build()
    );

    assertFalse(result.content().isEmpty());
    assertFalse(((TextContent) result.content().getFirst()).text().contains("Failed to read RULES.md"));
  }
}
