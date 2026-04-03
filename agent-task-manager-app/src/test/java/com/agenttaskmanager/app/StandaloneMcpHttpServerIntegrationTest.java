package com.agenttaskmanager.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StandaloneMcpHttpServerIntegrationTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void shouldServeMcpOverStandaloneEmbeddedTomcat() throws Exception {
    try (StandaloneAgentTaskManagerServer server = StandaloneAgentTaskManagerServer.start(
        "--server.port=0",
        "--app.security.mcp-no-auth-enabled=true",
        "--app.bridge.enabled=false",
        "--app.orchestration.autonomy-enabled=false",
        "--app.embedding.provider-order=hash",
        "--app.embedding.dimensions=32"
    )) {
      String sessionId = initializeSession(server.endpointUrl());
      JsonNode toolsListResponse = postSse(
          server.endpointUrl(),
          sessionId,
          Map.of("jsonrpc", "2.0", "id", "tools-1", "method", "tools/list", "params", Map.of())
      );

      assertTrue(toolsListResponse.toString().contains("createTaskBatch"));
      assertTrue(toolsListResponse.toString().contains("loadDashboardSummary"));
    }
  }

  private String initializeSession(String endpointUrl) throws Exception {
    HttpResponse<String> initializeResponse = postJson(
        endpointUrl,
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
                Map.of("name", "standalone-test", "version", "0.1.0")
            )
        )
    );

    assertEquals(200, initializeResponse.statusCode());
    return initializeResponse.headers()
        .firstValue("mcp-session-id")
        .orElseThrow(() -> new IllegalStateException("Missing mcp-session-id header"));
  }

  private HttpResponse<String> postJson(String endpointUrl, String sessionId, Map<String, Object> payload)
      throws Exception {
    HttpClient httpClient = HttpClient.newHttpClient();
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
        .uri(URI.create(endpointUrl))
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

  private JsonNode postSse(String endpointUrl, String sessionId, Map<String, Object> payload) throws Exception {
    HttpResponse<String> response = postJson(endpointUrl, sessionId, payload);

    assertEquals(200, response.statusCode());

    String data = response.body().lines()
        .filter(line -> line.startsWith("data: "))
        .map(line -> line.substring(6))
        .collect(Collectors.joining("\n"));

    return objectMapper.readTree(data);
  }
}
