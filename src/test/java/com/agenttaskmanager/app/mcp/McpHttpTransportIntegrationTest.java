package com.agenttaskmanager.app.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agenttaskmanager.app.support.IntegrationTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

class McpHttpTransportIntegrationTest extends IntegrationTestSupport {

  private static final String PASSWORD = "test-password";
  private static final String USERNAME = "test-agent";

  @Autowired
  private JdbcClient jdbcClient;

  @Autowired
  private ObjectMapper objectMapper;

  @LocalServerPort
  private int port;

  @BeforeEach
  void cleanup() {
    jdbcClient.sql("DELETE FROM agent_task_manager.validation_violations").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.validation_reports").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.patch_decisions").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.cleanup_reviews").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.shared_task_context").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.task_artifacts").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.worker_checkins").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.worker_task_leases").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.worker_tasks").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.agent_tasks WHERE task_kind = 'orchestration-batch'").update();
  }

  @Test
  void shouldCompleteHttpSessionHandshakeAndInvokeTools() throws Exception {
    String sessionId = initializeSession();

    HttpResponse<String> initializedResponse = postJson(
        sessionId,
        Map.of("jsonrpc", "2.0", "method", "notifications/initialized", "params", Map.of())
    );
    assertEquals(202, initializedResponse.statusCode());

    JsonNode toolsListResponse = postSse(
        sessionId,
        Map.of("jsonrpc", "2.0", "id", "tools-1", "method", "tools/list", "params", Map.of())
    );
    List<String> toolNames = extractToolNames(toolsListResponse);

    assertTrue(toolNames.contains("createTaskBatch"));
    assertTrue(toolNames.contains("loadDashboardSummary"));
    assertTrue(toolNames.contains("runIntegrationTests"));

    JsonNode createBatchResponse = postSse(
        sessionId,
        Map.of(
            "jsonrpc",
            "2.0",
            "id",
            "call-1",
            "method",
            "tools/call",
            "params",
            Map.of(
                "name",
                "createTaskBatch",
                "arguments",
                Map.of(
                    "projectKey",
                    "integration-http-mcp",
                    "sourceRepo",
                    "/srv/AgentTaskManager",
                    "title",
                    "HTTP MCP integration batch",
                    "multiAgentEnabled",
                    true,
                    "workerRoles",
                    List.of("research-worker", "implementation-worker")
                )
            )
        )
    );
    String taskId = createBatchResponse.at("/result/structuredContent/batch/taskId").asText();

    assertFalse(taskId.isBlank());

    JsonNode dashboardResponse = postSse(
        sessionId,
        Map.of(
            "jsonrpc",
            "2.0",
            "id",
            "call-2",
            "method",
            "tools/call",
            "params",
            Map.of("name", "loadDashboardSummary", "arguments", Map.of())
        )
    );

    assertTrue(dashboardResponse.at("/result/structuredContent/batches").isArray());
    assertTrue(dashboardResponse.toString().contains("integration-http-mcp"));
  }

  private List<String> extractToolNames(JsonNode response) {
    return response.at("/result/tools")
        .findValuesAsText("name")
        .stream()
        .collect(Collectors.toList());
  }

  private String initializeSession() throws Exception {
    HttpResponse<String> initializeResponse = postJson(
        null,
        Map.of(
            "jsonrpc",
            "2.0",
            "id",
            "init-1",
            "method",
            "initialize",
            "params",
            Map.of(
                "protocolVersion",
                "2025-03-26",
                "capabilities",
                Map.of(),
                "clientInfo",
                Map.of("name", "integration-test", "version", "0.1.0")
            )
        )
    );

    assertEquals(200, initializeResponse.statusCode());
    return initializeResponse.headers()
        .firstValue("mcp-session-id")
        .orElseThrow(() -> new IllegalStateException("Missing mcp-session-id header"));
  }

  private HttpResponse<String> postJson(String sessionId, Map<String, Object> payload) throws Exception {
    HttpClient httpClient = HttpClient.newHttpClient();
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
        .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
        .header("Authorization", authorizationHeader())
        .header("Accept", "application/json, text/event-stream")
        .header("Content-Type", "application/json");

    if (sessionId != null) {
      requestBuilder.header("Mcp-Session-Id", sessionId);
    }

    return httpClient.send(
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload))).build(),
        HttpResponse.BodyHandlers.ofString()
    );
  }

  private JsonNode postSse(String sessionId, Map<String, Object> payload) throws Exception {
    HttpResponse<String> response = postJson(sessionId, payload);

    assertEquals(200, response.statusCode());

    String data = response.body().lines()
        .filter(line -> line.startsWith("data: "))
        .map(line -> line.substring(6))
        .collect(Collectors.joining("\n"));

    return objectMapper.readTree(data);
  }

  private String authorizationHeader() {
    String token = Base64.getEncoder()
        .encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
    return "Basic " + token;
  }
}
