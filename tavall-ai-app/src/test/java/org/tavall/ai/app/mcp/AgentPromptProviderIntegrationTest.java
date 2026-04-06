package org.tavall.ai.app.mcp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.support.IntegrationTestSupport;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AgentPromptProviderIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private AgentPromptProvider agentPromptProvider;

  @Test
  void shouldEmbedSharedToolCombinationGuidanceInPromptTemplates() throws Exception {
    SyncPromptSpecification workerPrompt = agentPromptProvider.promptSpecifications().stream()
        .filter(specification -> "workerAgent".equals(specification.prompt().name()))
        .findFirst()
        .orElseThrow();

    GetPromptResult promptResult = invokePrompt(workerPrompt, "task-123");
    String body = ((TextContent) promptResult.messages().getFirst().content()).text();

    assertTrue(body.contains("Deterministic execution policy:"));
    assertTrue(body.contains("Memory policy:"));
    assertTrue(body.contains("Tool combination patterns:"));
    assertTrue(body.contains("runHarnessToolBundle(worker-context)"));
    assertTrue(body.contains("prepareGitBranch"));
    assertTrue(body.contains("createGitCommit"));
    assertTrue(body.contains("do not use downstream git mutation tools such as git_commit"));
    assertTrue(body.contains("loadTaskContext + loadValidationHistory + searchPriorFixes"));
    assertTrue(body.contains("local clean Java validation runs after worker execution"));
    assertTrue(body.contains("Final response contract:"));
  }

  private GetPromptResult invokePrompt(SyncPromptSpecification specification, String taskId) throws Exception {
    Object handler = handler(specification);
    Method method = Arrays.stream(handler.getClass().getMethods())
        .filter(candidate -> candidate.getDeclaringClass() != Object.class)
        .filter(candidate -> candidate.getParameterCount() == 2)
        .findFirst()
        .orElseThrow();
    return (GetPromptResult) method.invoke(
        handler,
        null,
        new GetPromptRequest(specification.prompt().name(), Map.of("taskId", taskId))
    );
  }

  private Object handler(SyncPromptSpecification specification) {
    RecordComponent component = Arrays.stream(specification.getClass().getRecordComponents())
        .filter(candidate -> !"prompt".equals(candidate.getName()))
        .findFirst()
        .orElseThrow();
    try {
      return component.getAccessor().invoke(specification);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Failed to access prompt handler.", exception);
    }
  }
}

