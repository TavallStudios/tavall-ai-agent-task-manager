package com.agenttaskmanager.app.mcp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class McpCatalogIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void shouldRegisterMcpToolsResourcesAndPrompts() {
    Set<String> toolNames = mcpSyncServer.listTools().stream()
        .map(tool -> tool.name())
        .collect(Collectors.toSet());

    assertTrue(toolNames.contains("createTaskBatch"));
    assertTrue(toolNames.contains("startDelegationRun"));
    assertTrue(toolNames.contains("appendDelegationRunEvent"));
    assertTrue(toolNames.contains("loadDelegationRun"));
    assertTrue(toolNames.contains("listDelegationRuns"));
    assertTrue(toolNames.contains("completeDelegationRun"));
    assertTrue(toolNames.contains("runArchUnitValidation"));
    assertTrue(toolNames.contains("runJavaLintValidation"));
    assertTrue(toolNames.contains("mergeWorkerOutputs"));
    assertTrue(toolNames.contains("loadDashboardSummary"));
    assertTrue(toolNames.contains("loadUniversalGuidance"));
    assertTrue(toolNames.contains("warmDashboardCache"));
    assertTrue(toolNames.contains("intakeHarnessTask"));
    assertTrue(toolNames.contains("routeHarnessTask"));
    assertTrue(toolNames.contains("loadHarnessState"));
    assertTrue(toolNames.contains("runHarnessToolBundle"));
    assertTrue(toolNames.contains("runHarnessApprovalGate"));
    assertTrue(toolNames.contains("loadCleanJavaRules"));
    assertTrue(toolNames.contains("loadCleanJavaMcpTaskContext"));
    assertTrue(toolNames.contains("loadCleanJavaTaskContext"));
    assertTrue(toolNames.contains("runCleanJavaArchUnit"));
    assertTrue(toolNames.contains("runCleanJavaSpoon"));
    assertTrue(toolNames.contains("validateCleanJavaPatchScope"));
    assertTrue(toolNames.contains("runCleanJavaHarness"));
    assertTrue(toolNames.contains("runJavaIntegrationHarness"));
    assertTrue(toolNames.contains("storeTaskEmbedding"));
    assertTrue(toolNames.contains("searchRelatedContexts"));
    assertTrue(toolNames.contains("searchPriorFixes"));
    assertTrue(toolNames.contains("loadRelatedSemanticContext"));
    assertTrue(toolNames.contains("attachSemanticContextToTask"));

    Set<String> resourceNames = mcpSyncServer.listResources().stream()
        .map(resource -> resource.name())
        .collect(Collectors.toSet());

    assertTrue(resourceNames.contains("AGENTS.md"));
    assertTrue(resourceNames.contains("RULES.md"));
    assertTrue(resourceNames.contains("UNIVERSAL.md"));
    assertTrue(resourceNames.contains("ARCHITECTURE.md"));
    assertTrue(resourceNames.contains("EXAMPLES.md"));
    assertTrue(resourceNames.contains("state://dashboard/summary"));
    assertTrue(resourceNames.contains("state://dashboard/workers"));

    Set<String> promptNames = mcpSyncServer.listPrompts().stream()
        .map(prompt -> prompt.name())
        .collect(Collectors.toSet());

    assertTrue(promptNames.contains("overseerAgent"));
    assertTrue(promptNames.contains("workerAgent"));
    assertTrue(promptNames.contains("cleanupAgent"));
  }
}
