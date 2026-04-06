package org.tavall.ai.app.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.StandaloneAgentTaskManagerServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackendProxyToolProviderTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void shouldExposeAndExecuteBackendProxyToolsThroughStandaloneServer(@TempDir Path tempDir) throws Exception {
    Path repoPath = initializeFixtureRepo(tempDir.resolve("fixture-repo"));
    Path registryPath = tempDir.resolve("tavall-ai-backends.json");
    Files.writeString(
        registryPath,
        """
        {
          "version": 1,
          "centralServer": "tavall-ai",
          "connectors": [
            {
              "id": "git",
              "displayName": "Git",
              "enabled": true,
              "transportKind": "stdio",
              "command": "git",
              "args": [],
              "env": {},
              "source": "bundle-import",
              "healthStatus": "unknown",
              "toolCache": [
                {
                  "name": "git_status",
                  "displayName": "Git Status",
                  "summary": "Load git status through the backend connector.",
                  "category": "Git"
                },
                {
                  "name": "git_commit",
                  "displayName": "Git Commit",
                  "summary": "Commit through the backend connector.",
                  "category": "Git"
                }
              ]
            }
          ]
        }
        """,
        StandardCharsets.UTF_8
    );

    try (StandaloneAgentTaskManagerServer server = StandaloneAgentTaskManagerServer.start(
        "--server.port=0",
        "--app.security.mcp-no-auth-enabled=true",
        "--app.bridge.enabled=false",
        "--app.orchestration.autonomy-enabled=false",
        "--app.embedding.provider-order=hash",
        "--app.embedding.dimensions=32",
        "--app.mcp.backend-registry-path=" + registryPath
    )) {
      String sessionId = initializeSession(server.endpointUrl());
      JsonNode toolsListResponse = postSse(
          server.endpointUrl(),
          sessionId,
          Map.of("jsonrpc", "2.0", "id", "tools-1", "method", "tools/list", "params", Map.of())
      );

      List<String> toolNames = toolsListResponse.at("/result/tools")
          .findValuesAsText("name")
          .stream()
          .collect(Collectors.toList());
      assertTrue(toolNames.contains("git.git_status"));
      assertTrue(!toolNames.contains("git.git_commit"));

      JsonNode toolCallResponse = postSse(
          server.endpointUrl(),
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
                  "git.git_status",
                  "arguments",
                  Map.of("repo_path", repoPath.toString())
              )
          )
      );

      assertEquals("git", toolCallResponse.at("/result/structuredContent/backendId").asText());
      assertEquals("git_status", toolCallResponse.at("/result/structuredContent/toolName").asText());
      assertTrue(toolCallResponse.toString().contains("output"));
    }
  }

  private Path initializeFixtureRepo(Path repoPath) throws Exception {
    Files.createDirectories(repoPath.resolve("src/main/java/example"));
    Files.writeString(
        repoPath.resolve("README.md"),
        "# Backend Proxy Fixture\n",
        StandardCharsets.UTF_8
    );
    run(repoPath, "git", "init", "-b", "main");
    run(repoPath, "git", "config", "user.email", "integration@example.com");
    run(repoPath, "git", "config", "user.name", "Integration Test");
    run(repoPath, "git", "add", ".");
    run(repoPath, "git", "commit", "-m", "Initial fixture");
    return repoPath;
  }

  private void run(Path repoPath, String... command) throws Exception {
    Process process = new ProcessBuilder(command)
        .directory(repoPath.toFile())
        .redirectErrorStream(true)
        .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IllegalStateException(String.join(" ", command) + " failed: " + output);
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
                Map.of("name", "backend-proxy-test", "version", "0.1.0")
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


