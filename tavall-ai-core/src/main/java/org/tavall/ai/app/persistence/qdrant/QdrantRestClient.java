package org.tavall.ai.app.persistence.qdrant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;
import org.tavall.ai.app.config.QdrantProperties;

final class QdrantRestClient {

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final QdrantProperties properties;
  private volatile String lastFailure = "";
  private volatile boolean successfulRequest;

  QdrantRestClient(HttpClient httpClient, ObjectMapper objectMapper, QdrantProperties properties) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  Map<String, Object> request(String path, Object body, String method) {
    return request(path, body, method, Set.of());
  }

  Map<String, Object> request(
      String path,
      Object body,
      String method,
      Set<Integer> allowedErrorStatuses
  ) {
    try {
      HttpRequest.Builder builder = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl(path)))
          .timeout(requestTimeout());
      if (!"DELETE".equals(method)) {
        builder.header("Content-Type", "application/json");
      }
      if (StringUtils.hasText(properties.getApiKey())) {
        builder.header("api-key", properties.getApiKey().strip());
      }
      HttpRequest request = builder.method(method, requestBody(method, body)).build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400 && !allowedErrorStatuses.contains(response.statusCode())) {
        throw new IllegalStateException("Qdrant request failed: " + response.body());
      }
      if (response.statusCode() >= 400) {
        clearFailure();
        return Map.of();
      }
      if (response.body() == null || response.body().isBlank()) {
        throw new IllegalStateException("Qdrant returned an empty response for " + method + " " + path + ".");
      }
      Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<>() {
      });
      if ("error".equalsIgnoreCase(String.valueOf(parsed.get("status")))) {
        throw new IllegalStateException("Qdrant returned an error response: " + response.body());
      }
      if (!parsed.containsKey("result")) {
        throw new IllegalStateException("Qdrant response did not include a result: " + response.body());
      }
      clearFailure();
      successfulRequest = true;
      return parsed;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      recordFailure(exception);
      throw new IllegalStateException("Qdrant request was interrupted.", exception);
    } catch (IOException exception) {
      recordFailure(exception);
      throw new IllegalStateException("Failed to talk to Qdrant.", exception);
    } catch (RuntimeException exception) {
      recordFailure(exception);
      throw exception;
    }
  }

  boolean isConfigured() {
    return StringUtils.hasText(properties.getBaseUrl());
  }

  String lastFailure() {
    return lastFailure;
  }

  boolean hasRecentFailure() {
    return !lastFailure.isBlank();
  }

  boolean hasSuccessfulRequest() {
    return successfulRequest;
  }

  private HttpRequest.BodyPublisher requestBody(String method, Object body) throws IOException {
    if ("DELETE".equals(method) || "GET".equals(method)) {
      return HttpRequest.BodyPublishers.noBody();
    }
    return HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
  }

  private String baseUrl(String path) {
    return properties.getBaseUrl().strip().replaceAll("/+$", "") + path;
  }

  private Duration requestTimeout() {
    Duration timeout = properties.getRequestTimeout();
    return timeout == null || timeout.isZero() || timeout.isNegative()
        ? Duration.ofSeconds(10)
        : timeout;
  }

  private void recordFailure(Exception exception) {
    lastFailure = exception.getMessage() == null
        ? exception.getClass().getSimpleName()
        : exception.getMessage();
  }

  private void clearFailure() {
    lastFailure = "";
  }
}
