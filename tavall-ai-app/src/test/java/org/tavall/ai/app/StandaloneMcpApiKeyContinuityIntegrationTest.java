package org.tavall.ai.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StandaloneMcpApiKeyContinuityIntegrationTest {

  private static final String TOKEN = "continuity-secret";

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void shouldAllowApiKeyAuthenticatedContinuityBootstrap() throws Exception {
    try (StandaloneAgentTaskManagerServer server = StandaloneAgentTaskManagerServer.start(
        "--server.port=0",
        "--app.bridge.enabled=false",
        "--app.orchestration.autonomy-enabled=false",
        "--app.embedding.provider-order=hash",
        "--app.embedding.dimensions=32",
        "--app.security.bootstrap-api-keys[0].token=" + TOKEN,
        "--app.security.bootstrap-api-keys[0].workspace-id=workspace-a",
        "--app.security.bootstrap-api-keys[0].user-id=user-a",
        "--app.security.bootstrap-api-keys[0].display-name=remote-client"
    )) {
      String sessionId = initializeSession(server.endpointUrl());
      JsonPayload toolResponse = postSse(
          server.endpointUrl(),
          sessionId,
          Map.of(
              "jsonrpc", "2.0",
              "id", "tool-1",
              "method", "tools/call",
              "params", Map.of(
                  "name", "searchSemanticContext",
                  "arguments", Map.of("projectKey", "api-key-project", "queryText", "continuity bootstrap")
              )
          )
      );
      assertTrue(toolResponse.body().contains("content"));

      HttpResponse<String> bootstrapResponse = HttpClient.newHttpClient().send(
          HttpRequest.newBuilder()
              .uri(URI.create(server.endpointUrl() + "/continuity/bootstrap?projectKey=api-key-project"))
              .header("Authorization", "Bearer " + TOKEN)
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofString()
      );

      assertEquals(200, bootstrapResponse.statusCode());
      assertTrue(bootstrapResponse.body().contains("summary"));
    }
  }

  private String initializeSession(String endpointUrl) throws Exception {
    HttpResponse<String> initializeResponse = postJson(
        endpointUrl,
        null,
        Map.of(
            "jsonrpc", "2.0",
            "id", "init-1",
            "method", "initialize",
            "params", Map.of(
                "protocolVersion", "2025-03-26",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "standalone-test", "version", "0.1.0")
            )
        )
    );
    assertEquals(200, initializeResponse.statusCode());
    return initializeResponse.headers().firstValue("mcp-session-id").orElseThrow();
  }

  private HttpResponse<String> postJson(String endpointUrl, String sessionId, Map<String, Object> payload)
      throws Exception {
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
        .uri(URI.create(endpointUrl))
        .header("Authorization", "Bearer " + TOKEN)
        .header("Accept", "application/json, text/event-stream")
        .header("Content-Type", "application/json");
    if (sessionId != null) {
      requestBuilder.header("Mcp-Session-Id", sessionId);
    }
    return HttpClient.newHttpClient().send(
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload))).build(),
        HttpResponse.BodyHandlers.ofString()
    );
  }

  private JsonPayload postSse(String endpointUrl, String sessionId, Map<String, Object> payload) throws Exception {
    HttpResponse<String> response = postJson(endpointUrl, sessionId, payload);
    assertEquals(200, response.statusCode());
    String data = response.body().lines()
        .filter(line -> line.startsWith("data: "))
        .map(line -> line.substring(6))
        .collect(Collectors.joining("\n"));
    return new JsonPayload(data);
  }

  private record JsonPayload(String body) {
  }
}

