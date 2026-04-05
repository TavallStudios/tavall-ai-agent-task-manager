package com.agenttaskmanager.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StandaloneDesktopControlPlaneIntegrationTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void shouldExposeDesktopOperationAndMcpPolicyEndpoints() throws Exception {
    try (StandaloneAgentTaskManagerServer server = startServer()) {
      JsonNode operations = sendJson(server, "GET", "/api/codex-client/operations", null);
      assertTrue(operations.path("groups").isArray());
      assertTrue(operations.path("groups").size() >= 1);

      JsonNode globalPolicy = sendJson(server, "GET", "/api/desktop/mcp-policy/global", null);
      assertEquals("global", globalPolicy.path("scopeKey").asText());

      JsonNode savedRepoPolicy = sendJson(
          server,
          "PUT",
          "/api/desktop/mcp-policy/repos/workspace-a",
          Map.of(
              "scopeKey",
              "workspace-a",
              "inheritGlobal",
              false,
              "servers",
              List.of(Map.of("serverName", "custom-server", "enabled", true)),
              "harnessPreferences",
              Map.of(
                  "diPreset",
                  "mcrspeedrun-annotation-di",
                  "languagePreset",
                  "java",
                  "customDiDescriptor",
                  "com.tjxjnoobie.api.dependency.injection.helpers.DependencyInjectorHelper",
                  "lintEnabled",
                  true,
                  "lintEngines",
                  List.of("checkstyle", "pmd", "error-prone"),
                  "lintStrictness",
                  "error",
                  "lintUnsupportedRepoPolicy",
                  "fail"
              ),
              "tools",
              List.of()
          )
      );
      assertEquals("workspace-a", savedRepoPolicy.path("scopeKey").asText());

      JsonNode preview = sendJson(server, "GET", "/api/desktop/mcp-policy/preview?scopeKey=workspace-a", null);
      assertTrue(preview.path("enabledServers").toString().contains("custom-server"));
      assertFalse(preview.path("enabledServers").toString().contains("agent-task-manager"));
      assertEquals("mcrspeedrun-annotation-di", preview.path("harnessPreferences").path("diPreset").asText());
      assertEquals("error", preview.path("harnessPreferences").path("lintStrictness").asText());
    }
  }

  @Test
  void shouldSupportRemoteRunnerProfileCrudAndSelection() throws Exception {
    try (StandaloneAgentTaskManagerServer server = startServer()) {
      JsonNode saved = sendJson(
          server,
          "PUT",
          "/api/desktop/remote-runners/runner-one",
          Map.of(
              "displayName",
              "Runner One",
              "baseUrl",
              "http://127.0.0.1:54123",
              "transportMode",
              "DIRECT_HTTP",
              "selected",
              true
          )
      );
      assertEquals("runner-one", saved.path("profileId").asText());
      assertTrue(saved.path("selected").asBoolean());

      JsonNode list = sendJson(server, "GET", "/api/desktop/remote-runners", null);
      assertTrue(list.size() >= 1);
      JsonNode runnerOne = findProfile(list, "runner-one");
      assertEquals("runner-one", runnerOne.path("profileId").asText());
      assertTrue(runnerOne.path("selected").asBoolean());

      JsonNode select = sendJson(server, "POST", "/api/desktop/remote-runners/runner-one/select", Map.of());
      assertEquals("runner-one", select.path("selected").asText());

      JsonNode deleted = sendJson(server, "DELETE", "/api/desktop/remote-runners/runner-one", null);
      assertEquals("runner-one", deleted.path("deleted").asText());

      JsonNode afterDelete = sendJson(server, "GET", "/api/desktop/remote-runners", null);
      assertFalse(containsProfile(afterDelete, "runner-one"));
    }
  }

  private StandaloneAgentTaskManagerServer startServer() {
    return StandaloneAgentTaskManagerServer.start(
        "--server.port=0",
        "--app.security.mcp-no-auth-enabled=true",
        "--app.bridge.enabled=false",
        "--app.orchestration.autonomy-enabled=false",
        "--app.embedding.provider-order=hash",
        "--app.embedding.dimensions=32"
    );
  }

  private JsonNode sendJson(
      StandaloneAgentTaskManagerServer server,
      String method,
      String path,
      Map<String, Object> body
  ) throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
        .uri(URI.create(server.endpointUrl().replace("/mcp", "") + path))
        .header("Accept", "application/json");

    if ("GET".equals(method)) {
      requestBuilder.GET();
    } else if ("DELETE".equals(method)) {
      requestBuilder.DELETE();
    } else {
      requestBuilder.header("Content-Type", "application/json");
      requestBuilder.method(
          method,
          HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body == null ? Map.of() : body))
      );
    }

    HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), "Unexpected status code for " + method + " " + path + ": " + response.body());
    return objectMapper.readTree(response.body());
  }

  private JsonNode findProfile(JsonNode list, String profileId) {
    for (JsonNode item : list) {
      if (profileId.equals(item.path("profileId").asText())) {
        return item;
      }
    }
    throw new IllegalStateException("Expected profile not found: " + profileId);
  }

  private boolean containsProfile(JsonNode list, String profileId) {
    for (JsonNode item : list) {
      if (profileId.equals(item.path("profileId").asText())) {
        return true;
      }
    }
    return false;
  }
}
