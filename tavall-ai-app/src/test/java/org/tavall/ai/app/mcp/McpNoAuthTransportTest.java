package org.tavall.ai.app.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "app.security.mcp-no-auth-enabled=true",
    "server.servlet.context-path=/tavall-ai"
})
class McpNoAuthTransportTest extends IntegrationTestSupport {

  @Autowired
  private ObjectMapper objectMapper;

  @LocalServerPort
  private int port;

  @Test
  void shouldAllowHandshakeWithoutAuthorizationWhenMcpNoAuthIsEnabled() throws Exception {
    String sessionId = initializeSession();
    JsonNode toolsListResponse = postSse(
        sessionId,
        Map.of("jsonrpc", "2.0", "id", "tools-1", "method", "tools/list", "params", Map.of())
    );

    assertTrue(toolsListResponse.toString().contains("loadDashboardSummary"));
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
        .uri(URI.create("http://127.0.0.1:" + port + "/tavall-ai/mcp"))
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
}


