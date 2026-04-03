package com.agenttaskmanager.app.cli;

import com.agenttaskmanager.app.config.McpServerProperties;
import com.agenttaskmanager.app.config.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RemoteMcpSmokeService {

  private final McpJsonMapper mcpJsonMapper;
  private final McpServerProperties mcpServerProperties;
  private final ObjectMapper objectMapper;
  private final SecurityProperties securityProperties;

  public RemoteMcpSmokeService(
      McpJsonMapper mcpJsonMapper,
      McpServerProperties mcpServerProperties,
      ObjectMapper objectMapper,
      SecurityProperties securityProperties
  ) {
    this.mcpJsonMapper = mcpJsonMapper;
    this.mcpServerProperties = mcpServerProperties;
    this.objectMapper = objectMapper;
    this.securityProperties = securityProperties;
  }

  public RemoteMcpSmokeResult runSmoke() {
    return runSmoke(
        mcpServerProperties.getBaseUrl(),
        mcpServerProperties.getEndpoint(),
        securityProperties.getUsername(),
        securityProperties.getPassword()
    );
  }

  public RemoteMcpSmokeResult runSmoke(String baseUrl, String endpoint, String username, String password) {
    RemoteEndpoint remoteEndpoint = resolveRemoteEndpoint(baseUrl, endpoint);
    HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(remoteEndpoint.baseUrl())
        .endpoint(remoteEndpoint.endpoint())
        .jsonMapper(mcpJsonMapper)
        .httpRequestCustomizer((HttpRequest.Builder builder, String method, java.net.URI uri, String body, io.modelcontextprotocol.common.McpTransportContext context) ->
            applyAuthorization(builder, username, password))
        .build();

    McpSyncClient client = McpClient.sync(transport)
        .requestTimeout(Duration.ofSeconds(20))
        .initializationTimeout(Duration.ofSeconds(20))
        .clientInfo(new Implementation("AgentTaskManager CLI", "0.1.0"))
        .build();

    try {
      var initializeResult = client.initialize();
      var toolsResult = client.listTools();
      var dashboardSummary = client.callTool(new CallToolRequest("loadDashboardSummary", Map.of()));

      return new RemoteMcpSmokeResult(
          remoteEndpoint.baseUrl(),
          remoteEndpoint.endpoint(),
          initializeResult.protocolVersion(),
          initializeResult.serverInfo().name(),
          initializeResult.serverInfo().version(),
          toolsResult.tools().stream().map(tool -> tool.name()).sorted().toList(),
          List.of(),
          List.of(),
          objectMapper.convertValue(dashboardSummary.structuredContent(), objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class))
      );
    } finally {
      client.close();
    }
  }

  private String basicAuthorization(String username, String password) {
    String token = Base64.getEncoder()
        .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    return "Basic " + token;
  }

  private HttpRequest.Builder applyAuthorization(HttpRequest.Builder builder, String username, String password) {
    if (password == null || password.isBlank()) {
      return builder;
    }
    return builder.header("Authorization", basicAuthorization(username, password));
  }

  private RemoteEndpoint resolveRemoteEndpoint(String baseUrl, String endpoint) {
    URI remoteUri = URI.create(baseUrl);
    String resolvedPath = joinPath(remoteUri.getPath(), endpoint);
    String normalizedBaseUrl = remoteUri.getScheme() + "://" + remoteUri.getAuthority();
    return new RemoteEndpoint(normalizedBaseUrl, resolvedPath);
  }

  private String joinPath(String basePath, String endpoint) {
    String normalizedBasePath = normalizeSegment(basePath);
    String normalizedEndpoint = normalizeSegment(endpoint);

    if (normalizedBasePath.isEmpty()) {
      return normalizedEndpoint.isEmpty() ? "/" : normalizedEndpoint;
    }

    if (normalizedEndpoint.isEmpty() || "/".equals(normalizedEndpoint)) {
      return normalizedBasePath;
    }

    if (normalizedEndpoint.startsWith(normalizedBasePath + "/")
        || normalizedEndpoint.equals(normalizedBasePath)) {
      return normalizedEndpoint;
    }

    return normalizedBasePath + normalizedEndpoint;
  }

  private String normalizeSegment(String value) {
    if (value == null || value.isBlank() || "/".equals(value)) {
      return "";
    }
    String normalizedValue = value.startsWith("/") ? value : "/" + value;
    return normalizedValue.endsWith("/") ? normalizedValue.substring(0, normalizedValue.length() - 1) : normalizedValue;
  }

  private record RemoteEndpoint(String baseUrl, String endpoint) {
  }
}
