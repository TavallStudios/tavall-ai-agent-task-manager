package com.agenttaskmanager.app.mcp;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class McpInteractionThreadResolver {

  public McpInteractionThreadContext resolveTool(
      McpSyncServerExchange exchange,
      String toolName,
      Map<String, Object> arguments,
      Map<String, Object> meta
  ) {
    return resolve(exchange, "mcp-tool", toolName, arguments, meta, "tool");
  }

  public McpInteractionThreadContext resolvePrompt(
      McpSyncServerExchange exchange,
      String promptName,
      Map<String, Object> arguments,
      Map<String, Object> meta
  ) {
    return resolve(exchange, "mcp-prompt", promptName, arguments, meta, "prompt");
  }

  public McpInteractionThreadContext resolveResource(
      McpSyncServerExchange exchange,
      String uri,
      Map<String, Object> meta
  ) {
    return resolve(exchange, "mcp-resource", uri, Map.of("uri", uri), meta, "resource");
  }

  private McpInteractionThreadContext resolve(
      McpSyncServerExchange exchange,
      String interactionType,
      String interactionName,
      Map<String, Object> payload,
      Map<String, Object> meta,
      String summaryLabel
  ) {
    Map<String, Object> safePayload = payload == null ? Map.of() : Map.copyOf(payload);
    Map<String, Object> safeMeta = meta == null ? Map.of() : Map.copyOf(meta);
    String sessionId = exchange == null ? "" : normalize(exchange.sessionId());
    String projectKey = firstNonBlank(safeMeta, safePayload, "projectKey");
    String repoPath = firstNonBlank(safeMeta, safePayload, "repoPath");
    String threadKey = firstNonBlank(safeMeta, safePayload, "threadKey", "conversationKey");
    if (threadKey.isBlank()) {
      String scope = !projectKey.isBlank() ? projectKey : repoPath;
      threadKey = scope.isBlank()
          ? "mcp-http:" + fallback(sessionId, "sessionless")
          : "mcp-http:" + fallback(sessionId, "sessionless") + ":" + scope;
    }
    String requestedBy = exchange != null && exchange.getClientInfo() != null
        ? fallback(exchange.getClientInfo().name(), "mcp-client")
        : "mcp-client";
    String payloadSummary = safePayload.isEmpty() ? "" : safePayload.toString();
    return new McpInteractionThreadContext(
        interactionType,
        interactionName,
        sessionId,
        threadKey,
        projectKey,
        repoPath,
        requestedBy,
        "mcp-http",
        summaryLabel + "=" + interactionName + (payloadSummary.isBlank() ? "" : "\narguments=" + payloadSummary),
        interactionName + " " + payloadSummary,
        safePayload,
        safeMeta
    );
  }

  private String firstNonBlank(Map<String, Object> first, Map<String, Object> second, String... keys) {
    for (String key : keys) {
      String firstValue = read(first, key);
      if (!firstValue.isBlank()) {
        return firstValue;
      }
      String secondValue = read(second, key);
      if (!secondValue.isBlank()) {
        return secondValue;
      }
    }
    return "";
  }

  private String read(Map<String, Object> payload, String key) {
    Object value = payload.get(key);
    return value == null ? "" : String.valueOf(value).strip();
  }

  private String normalize(String value) {
    return value == null ? "" : value.strip();
  }

  private String fallback(String value, String fallback) {
    String normalized = normalize(value);
    return normalized.isBlank() ? fallback : normalized;
  }
}
