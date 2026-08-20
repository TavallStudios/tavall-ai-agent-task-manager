package org.tavall.ai.app.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

final class MemoryMcpTestServer implements AutoCloseable {

  private final HttpServer server;
  private final ObjectMapper objectMapper;
  private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
  private final List<String> protocolVersions = new CopyOnWriteArrayList<>();
  private String toolResultText = "ok";
  private ResponseMode responseMode = ResponseMode.NORMAL;

  MemoryMcpTestServer(ObjectMapper objectMapper) throws IOException {
    this.objectMapper = objectMapper;
    this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    this.server.createContext("/mcp", this::handle);
    this.server.start();
  }

  String endpoint() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
  }

  void setToolResultText(String toolResultText) {
    this.toolResultText = toolResultText;
  }

  void setResponseMode(ResponseMode responseMode) {
    this.responseMode = responseMode;
  }

  List<JsonNode> requests() {
    return List.copyOf(requests);
  }

  List<String> protocolVersions() {
    return List.copyOf(protocolVersions);
  }

  JsonNode lastToolCall() {
    return requests.stream()
        .filter(request -> "tools/call".equals(request.path("method").asText()))
        .reduce((left, right) -> right)
        .orElseThrow();
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    JsonNode request = objectMapper.readTree(exchange.getRequestBody());
    requests.add(request);
    String protocolVersion = exchange.getRequestHeaders().getFirst("MCP-Protocol-Version");
    if (protocolVersion != null) {
      protocolVersions.add(protocolVersion);
    }
    String method = request.path("method").asText();
    if (responseMode == ResponseMode.HTTP_500) {
      send(exchange, 500, Map.of("error", "fixture failure"));
      return;
    }
    if ("initialize".equals(method)) {
      exchange.getResponseHeaders().add("Mcp-Session-Id", "memory-test-session");
      send(exchange, Map.of(
          "jsonrpc", "2.0",
          "id", request.path("id").asInt(),
          "result", Map.of(
              "protocolVersion", "2025-06-18",
              "capabilities", Map.of(),
              "serverInfo", Map.of("name", "memory-test", "version", "1")
          )
      ));
      return;
    }
    if ("notifications/initialized".equals(method)) {
      exchange.sendResponseHeaders(202, -1);
      exchange.close();
      return;
    }
    if ("tools/call".equals(method)) {
      if (responseMode == ResponseMode.STALE_SESSION) {
        send(exchange, 404, Map.of("error", "stale session"));
        return;
      }
      if (responseMode == ResponseMode.TIMEOUT) {
        try {
          Thread.sleep(500L);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
        }
      }
      if (responseMode == ResponseMode.EMPTY) {
        sendRaw(exchange, 200, "");
        return;
      }
      if (responseMode == ResponseMode.INVALID_JSON) {
        sendRaw(exchange, 200, "{not-json");
        return;
      }
      if (responseMode == ResponseMode.TOOL_ERROR) {
        send(exchange, 200, Map.of(
            "jsonrpc", "2.0",
            "id", request.path("id").asInt(),
            "result", Map.of("isError", true, "content", List.of(Map.of("type", "text", "text", "bad tool")))
        ));
        return;
      }
      int responseId = responseMode == ResponseMode.MISMATCHED_ID
          ? request.path("id").asInt() + 100
          : request.path("id").asInt();
      Map<String, Object> response = Map.of(
          "jsonrpc", "2.0",
          "id", responseId,
          "result", Map.of("content", List.of(Map.of("type", "text", "text", toolResultText)))
      );
      if (responseMode == ResponseMode.SSE) {
        sendRaw(exchange, 200, "event: message\ndata: " + objectMapper.writeValueAsString(response) + "\n\n");
        return;
      }
      send(exchange, Map.of(
          "jsonrpc", "2.0",
          "id", responseId,
          "result", Map.of("content", List.of(Map.of("type", "text", "text", toolResultText)))
      ));
      return;
    }
    send(exchange, Map.of(
        "jsonrpc", "2.0",
        "id", request.path("id").asInt(),
        "error", Map.of("code", -32601, "message", "method not found")
    ));
  }

  private void send(HttpExchange exchange, Map<String, Object> body) throws IOException {
    send(exchange, 200, body);
  }

  private void send(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
    byte[] bytes = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  private void sendRaw(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  enum ResponseMode {
    NORMAL,
    SSE,
    HTTP_500,
    INVALID_JSON,
    EMPTY,
    TOOL_ERROR,
    MISMATCHED_ID,
    TIMEOUT,
    STALE_SESSION
  }
}
