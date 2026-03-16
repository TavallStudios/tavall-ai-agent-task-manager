package com.agenttaskmanager.app.persistence.qdrant;

import com.agenttaskmanager.app.config.EmbeddingProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class LocalCommandEmbeddingProvider implements EmbeddingProvider {

  private final EmbeddingProperties embeddingProperties;
  private final ObjectMapper objectMapper;

  public LocalCommandEmbeddingProvider(EmbeddingProperties embeddingProperties, ObjectMapper objectMapper) {
    this.embeddingProperties = embeddingProperties;
    this.objectMapper = objectMapper;
  }

  @Override
  public String providerId() {
    return "local";
  }

  @Override
  public boolean isConfigured() {
    return embeddingProperties.getLocalCommand() != null && !embeddingProperties.getLocalCommand().isBlank();
  }

  @Override
  public EmbeddingVectorResult embed(String title, String text, EmbeddingPurpose purpose) {
    return embedText(title, text, purpose);
  }

  private EmbeddingVectorResult embedText(String title, String text, EmbeddingPurpose embeddingPurpose) {
    if (isConfigured()) {
      Map<String, Object> request = new LinkedHashMap<>();
      request.put("model", embeddingProperties.getLocalModel());
      request.put("dimensions", embeddingProperties.getDimensions());
      request.put("purpose", embeddingPurpose.name());
      request.put("title", title);
      request.put("text", text == null ? "" : text);
      String output = runCommand(request);
      Map<String, Object> payload = readResponse(output);
      List<Double> vector = readVector(payload);
      if (vector.size() == embeddingProperties.getDimensions()) {
        return new EmbeddingVectorResult(
            String.valueOf(payload.getOrDefault("provider", providerId())),
            String.valueOf(payload.getOrDefault("model", embeddingProperties.getLocalModel())),
            vector
        );
      }
      throw new IllegalStateException("Local embedding command returned a vector with the wrong size.");
    }
    throw new IllegalStateException("Local command embeddings are not configured.");
  }

  private String runCommand(Map<String, Object> request) {
    try {
      Process process = new ProcessBuilder("bash", "-lc", embeddingProperties.getLocalCommand())
          .redirectErrorStream(true)
          .start();
      process.getOutputStream().write(objectMapper.writeValueAsBytes(request));
      process.getOutputStream().close();
      boolean finished = process.waitFor(embeddingProperties.getLocalTimeoutSeconds(), TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new IllegalStateException("Local embedding command timed out.");
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (process.exitValue() == 0) {
        return output;
      }
      throw new IllegalStateException("Local embedding command failed: " + output);
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException("Failed to run the local embedding command.", exception);
    }
  }

  private Map<String, Object> readResponse(String output) {
    try {
      return objectMapper.readValue(output, new TypeReference<>() {
      });
    } catch (IOException exception) {
      throw new IllegalStateException("Local embedding command did not return valid JSON.", exception);
    }
  }

  private List<Double> readVector(Map<String, Object> payload) {
    Object vector = payload.get("vector");
    if (vector instanceof List<?> values) {
      return values.stream()
          .map(this::readDouble)
          .toList();
    }
    throw new IllegalStateException("Local embedding command did not include a vector.");
  }

  private Double readDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return Double.parseDouble(String.valueOf(value));
  }
}
