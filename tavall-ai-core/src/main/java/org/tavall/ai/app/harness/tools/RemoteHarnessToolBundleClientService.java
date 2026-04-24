package org.tavall.ai.app.harness.tools;

import org.tavall.ai.app.config.CodexExecutionProperties;
import org.tavall.ai.app.config.McpServerProperties;
import org.tavall.ai.app.config.SecurityProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RemoteHarnessToolBundleClientService {

  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  private final CodexExecutionProperties codexExecutionProperties;
  private final McpJsonMapper mcpJsonMapper;
  private final McpServerProperties mcpServerProperties;
  private final ObjectMapper objectMapper;
  private final SecurityProperties securityProperties;
  private final SharedRepoSnapshotService sharedRepoSnapshotService;

  public RemoteHarnessToolBundleClientService(
      CodexExecutionProperties codexExecutionProperties,
      McpJsonMapper mcpJsonMapper,
      McpServerProperties mcpServerProperties,
      ObjectMapper objectMapper,
      SecurityProperties securityProperties,
      SharedRepoSnapshotService sharedRepoSnapshotService
  ) {
    this.codexExecutionProperties = codexExecutionProperties;
    this.mcpJsonMapper = mcpJsonMapper;
    this.mcpServerProperties = mcpServerProperties;
    this.objectMapper = objectMapper;
    this.securityProperties = securityProperties;
    this.sharedRepoSnapshotService = sharedRepoSnapshotService;
  }

  public boolean isEnabled() {
    return codexExecutionProperties.isRemoteToolExecutionEnabled()
        && hasValue(mcpServerProperties.getBaseUrl())
        && hasValue(mcpServerProperties.getEndpoint())
        && hasValue(securityProperties.getPassword());
  }

  public HarnessToolBundleResult loadRemoteRepoContext(HarnessToolBundleRequest request) {
    RemoteEndpoint remoteEndpoint = resolveRemoteEndpoint();
    HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(remoteEndpoint.baseUrl())
        .endpoint(remoteEndpoint.endpoint())
        .jsonMapper(mcpJsonMapper)
        .httpRequestCustomizer(
            (HttpRequest.Builder builder, String method, java.net.URI uri, String body, io.modelcontextprotocol.common.McpTransportContext context) ->
                builder.header("Authorization", basicAuthorization())
        )
        .build();

    try (McpSyncClient client = McpClient.sync(transport)
        .requestTimeout(TIMEOUT)
        .initializationTimeout(TIMEOUT)
        .clientInfo(new Implementation("AgentTaskManager Harness", "0.1.0"))
        .build()) {
      client.initialize();
      String remoteRepoPath = resolveRemoteRepoPath(client, request.repoPath());
      CallToolResult result = client.callTool(
          new CallToolRequest("runHarnessToolBundle", repoContextArguments(request, remoteRepoPath))
      );
      if (Boolean.TRUE.equals(result.isError())) {
        throw new IllegalStateException("Remote runHarnessToolBundle(repo-context) returned an error.");
      }
      return extractBundleResult(result);
    }
  }

  private Map<String, Object> repoContextArguments(HarnessToolBundleRequest request, String repoPath) {
    Map<String, Object> arguments = new LinkedHashMap<>();
    arguments.put("bundleName", HarnessToolBundleType.REPO_CONTEXT.value());
    putIfPresent(arguments, "taskId", request.taskId());
    putIfPresent(arguments, "workerTaskId", request.workerTaskId());
    putIfPresent(arguments, "projectKey", request.projectKey());
    putIfPresent(arguments, "repoPath", repoPath);
    putIfPresent(arguments, "queryText", request.queryText());
    if (request.limit() != null) {
      arguments.put("limit", request.limit());
    }
    return arguments;
  }

  private String resolveRemoteRepoPath(McpSyncClient client, String repoPath) {
    if (!hasValue(repoPath)) {
      return repoPath;
    }
    try {
      Path localRepoPath = Path.of(repoPath).toAbsolutePath().normalize();
      if (!Files.isDirectory(localRepoPath)) {
        return repoPath;
      }
      CallToolResult result = client.callTool(
          new CallToolRequest(
              "stageSharedRepoSnapshot",
              Map.of(
                  "repoName", localRepoPath.getFileName() == null ? "repo" : localRepoPath.getFileName().toString(),
                  "archiveBase64", sharedRepoSnapshotService.createArchiveBase64(localRepoPath)
              )
          )
      );
      if (Boolean.TRUE.equals(result.isError())) {
        throw new IllegalStateException("Remote stageSharedRepoSnapshot returned an error.");
      }
      Map<String, Object> payload = objectMapper.convertValue(
          result.structuredContent(),
          new TypeReference<Map<String, Object>>() {
          }
      );
      Object remotePath = payload == null ? null : payload.get("repoPath");
      if (remotePath == null && payload != null) {
        Object nested = payload.get("stageSharedRepoSnapshotResponse");
        if (nested instanceof Map<?, ?> nestedMap) {
          remotePath = nestedMap.get("repoPath");
        }
      }
      if (remotePath instanceof String remoteRepoPath && hasValue(remoteRepoPath)) {
        return remoteRepoPath;
      }
      throw new IllegalStateException("Remote stageSharedRepoSnapshot did not return a repoPath.");
    } catch (Exception exception) {
      if (exception instanceof IllegalStateException) {
        throw (IllegalStateException) exception;
      }
      return repoPath;
    }
  }

  private HarnessToolBundleResult extractBundleResult(CallToolResult result) {
    Map<String, Object> payload = objectMapper.convertValue(
        result.structuredContent(),
        new TypeReference<Map<String, Object>>() {
        }
    );
    if (payload == null || payload.isEmpty()) {
      throw new IllegalStateException("Remote runHarnessToolBundle(repo-context) did not return structured content.");
    }
    Object bundleResult = payload.getOrDefault("bundleResult", payload);
    return objectMapper.convertValue(bundleResult, HarnessToolBundleResult.class);
  }

  private String basicAuthorization() {
    String token = Base64.getEncoder()
        .encodeToString((securityProperties.getUsername() + ":" + securityProperties.getPassword())
            .getBytes(StandardCharsets.UTF_8));
    return "Basic " + token;
  }

  private boolean hasValue(String value) {
    return value != null && !value.isBlank();
  }

  private RemoteEndpoint resolveRemoteEndpoint() {
    URI remoteUri = URI.create(mcpServerProperties.getBaseUrl());
    String resolvedPath = joinPath(remoteUri.getPath(), mcpServerProperties.getEndpoint());
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
    if (!hasValue(value) || "/".equals(value)) {
      return "";
    }
    String normalizedValue = value.startsWith("/") ? value : "/" + value;
    return normalizedValue.endsWith("/") ? normalizedValue.substring(0, normalizedValue.length() - 1) : normalizedValue;
  }

  private void putIfPresent(Map<String, Object> arguments, String key, String value) {
    if (hasValue(value)) {
      arguments.put(key, value);
    }
  }

  private record RemoteEndpoint(String baseUrl, String endpoint) {
  }
}

