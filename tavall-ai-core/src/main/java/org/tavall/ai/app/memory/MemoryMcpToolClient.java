package org.tavall.ai.app.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MemoryMcpToolClient {

  private static final String MCP_PROTOCOL_VERSION = "2025-06-18";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public MemoryMcpToolClient(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  }

  /** Calls one tool on a Streamable HTTP MCP server using an isolated short-lived MCP session. */
  public JsonNode callTool(
      String endpoint,
      String apiKey,
      Duration timeout,
      String toolName,
      Map<String, Object> arguments
  ) {
    String normalizedEndpoint = requireEndpoint(endpoint);
    if (toolName == null || toolName.isBlank()) {
      throw new IllegalArgumentException("MCP tool name is required.");
    }
    Duration normalizedTimeout = normalizeTimeout(timeout);
    HttpResponse<String> initialize = post(
        normalizedEndpoint,
        apiKey,
        normalizedTimeout,
        null,
        null,
        requestPayload(
            1,
            "initialize",
            Map.of(
                "protocolVersion", MCP_PROTOCOL_VERSION,
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "tavall-ai-memory", "version", "0.1.0")
            )
        ),
        true
    );
    JsonNode initializeEnvelope = parseEnvelope(initialize.body(), "initialize", 1);
    JsonNode initializeResult = initializeEnvelope.path("result");
    String negotiatedProtocol = initializeResult.path("protocolVersion").asText("").strip();
    if (negotiatedProtocol.isBlank()) {
      throw new IllegalStateException("MCP initialize response did not negotiate a protocol version.");
    }
    String sessionId = initialize.headers().firstValue("Mcp-Session-Id").orElse("");
    post(
        normalizedEndpoint,
        apiKey,
        normalizedTimeout,
        sessionId,
        negotiatedProtocol,
        notificationPayload("notifications/initialized", Map.of()),
        false
    );
    HttpResponse<String> response = post(
        normalizedEndpoint,
        apiKey,
        normalizedTimeout,
        sessionId,
        negotiatedProtocol,
        requestPayload(
            2,
            "tools/call",
            Map.of("name", toolName, "arguments", arguments == null ? Map.of() : arguments)
        ),
        true
    );
    JsonNode envelope = parseEnvelope(response.body(), "tools/call", 2);
    JsonNode result = envelope.path("result");
    if (result.path("isError").asBoolean(false)) {
      throw new IllegalStateException("MCP tool call returned an error result: " + summarize(result));
    }
    return result;
  }

  private HttpResponse<String> post(
      String endpoint,
      String apiKey,
      Duration timeout,
      String sessionId,
      String protocolVersion,
      Map<String, Object> payload,
      boolean expectBody
  ) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
        .timeout(timeout)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json, text/event-stream")
        .POST(HttpRequest.BodyPublishers.ofString(writeJson(payload)));
    if (protocolVersion != null && !protocolVersion.isBlank()) {
      builder.header("MCP-Protocol-Version", protocolVersion);
    }
    if (sessionId != null && !sessionId.isBlank()) {
      builder.header("Mcp-Session-Id", sessionId);
    }
    if (apiKey != null && !apiKey.isBlank()) {
      builder.header("Authorization", "Bearer " + apiKey.strip());
    }
    try {
      HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "MCP HTTP " + response.statusCode() + " from " + endpoint + ": " + summarize(response.body())
        );
      }
      if (expectBody && (response.body() == null || response.body().isBlank())) {
        throw new IllegalStateException("MCP server returned an empty response body from " + endpoint);
      }
      return response;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("MCP request interrupted for " + endpoint, exception);
    } catch (IOException exception) {
      throw new IllegalStateException("MCP request failed for " + endpoint, exception);
    }
  }

  private Map<String, Object> requestPayload(int id, String method, Map<String, Object> params) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("jsonrpc", "2.0");
    payload.put("id", id);
    payload.put("method", method);
    payload.put("params", params);
    return payload;
  }

  private Map<String, Object> notificationPayload(String method, Map<String, Object> params) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("jsonrpc", "2.0");
    payload.put("method", method);
    payload.put("params", params);
    return payload;
  }

  private JsonNode parseEnvelope(String body, String operation, int expectedId) {
    JsonNode envelope = parseResponse(body);
    if (!envelope.isObject() || !"2.0".equals(envelope.path("jsonrpc").asText())) {
      throw new IllegalStateException("MCP " + operation + " response was not a JSON-RPC 2.0 object.");
    }
    if (!envelope.path("id").canConvertToInt() || envelope.path("id").asInt() != expectedId) {
      throw new IllegalStateException("MCP " + operation + " response id did not match the request.");
    }
    if (envelope.hasNonNull("error")) {
      throw new IllegalStateException("MCP " + operation + " failed: " + summarize(envelope.path("error")));
    }
    if (!envelope.has("result") || !envelope.path("result").isObject()) {
      throw new IllegalStateException("MCP " + operation + " response did not include a result.");
    }
    return envelope;
  }

  private JsonNode parseResponse(String body) {
    String normalized = body == null ? "" : body.strip();
    if (normalized.isBlank()) {
      throw new IllegalStateException("MCP server returned an empty response body.");
    }
    if (normalized.startsWith("{")) {
      return readJson(normalized);
    }
    String lastData = normalized.lines()
        .filter(line -> line.startsWith("data:"))
        .map(line -> line.substring("data:".length()).strip())
        .filter(line -> !line.isBlank())
        .reduce((left, right) -> right)
        .orElseThrow(() -> new IllegalStateException("MCP server returned unsupported response content."));
    return readJson(lastData);
  }

  private JsonNode readJson(String value) {
    try {
      return objectMapper.readTree(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("MCP server returned invalid JSON.", exception);
    }
  }

  private String writeJson(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize MCP request.", exception);
    }
  }

  private String requireEndpoint(String endpoint) {
    if (endpoint == null || endpoint.isBlank()) {
      throw new IllegalArgumentException("MCP endpoint is required.");
    }
    return endpoint.strip().replaceAll("/+$", "");
  }

  private Duration normalizeTimeout(Duration timeout) {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      return Duration.ofSeconds(6);
    }
    return timeout;
  }

  private String summarize(String value) {
    String normalized = Optional.ofNullable(value).orElse("").replaceAll("\\s+", " ").strip();
    return normalized.length() <= 240 ? normalized : normalized.substring(0, 237) + "...";
  }

  private String summarize(JsonNode value) {
    return summarize(value == null ? "" : value.toString());
  }
}
