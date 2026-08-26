package org.tavall.ai.app.persistence.qdrant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class QdrantTestServer implements AutoCloseable {

  private final HttpServer server;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final List<String> requests = new ArrayList<>();
  private int schemaSize = 2;
  private String queryResponse = "{\"status\":\"ok\",\"result\":[]}";
  private String apiKey = "";

  QdrantTestServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/collections", this::handle);
    server.start();
  }

  String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  void setSchemaSize(int schemaSize) {
    this.schemaSize = schemaSize;
  }

  void setQueryResponse(String queryResponse) {
    this.queryResponse = queryResponse;
  }

  void setExpectedApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  List<String> requests() {
    return List.copyOf(requests);
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getPath();
    requests.add(method + " " + path);
    if (!apiKey.isBlank() && !apiKey.equals(exchange.getRequestHeaders().getFirst("api-key"))) {
      send(exchange, 401, Map.of("status", "error", "result", "missing api key"));
      return;
    }
    if ("GET".equals(method) && path.matches("/collections/[^/]+")) {
      send(exchange, 200, Map.of(
          "status", "ok",
          "result", Map.of("config", Map.of("params", Map.of(
              "vectors", Map.of("size", schemaSize, "distance", "Cosine")
          )))
      ));
      return;
    }
    if ("POST".equals(method) && path.endsWith("/points/query")) {
      sendRaw(exchange, 200, queryResponse);
      return;
    }
    send(exchange, 200, Map.of("status", "ok", "result", true));
  }

  private void send(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
    sendRaw(exchange, status, objectMapper.writeValueAsString(body));
  }

  private void sendRaw(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
