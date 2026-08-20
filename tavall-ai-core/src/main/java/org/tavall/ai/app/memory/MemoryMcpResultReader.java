package org.tavall.ai.app.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MemoryMcpResultReader {

  private static final ObjectMapper JSON = new ObjectMapper();

  public boolean hasError(JsonNode result) {
    if (result == null || result.isMissingNode() || result.isNull()) {
      return true;
    }
    if (result.path("isError").asBoolean(false)) {
      return true;
    }
    return directError(result.path("structuredContent"))
        || directError(result.path("content"));
  }

  public String text(JsonNode result) {
    return text(result, Integer.MAX_VALUE);
  }

  public String text(JsonNode result, int maxCharacters) {
    if (result == null || result.isMissingNode() || result.isNull()) {
      return "";
    }
    List<String> parts = new ArrayList<>();
    JsonNode content = result.path("content");
    if (content.isArray()) {
      for (JsonNode item : content) {
        String text = item.path("text").asText().strip();
        if (!text.isBlank()) {
          parts.add(text);
        }
      }
    }
    if (!parts.isEmpty()) {
      return limit(String.join("\n", parts), maxCharacters);
    }
    JsonNode structured = result.path("structuredContent");
    if (!structured.isMissingNode() && !structured.isNull()) {
      return limit(structured.toString(), maxCharacters);
    }
    if (result.isTextual()) {
      return limit(result.asText(), maxCharacters);
    }
    return result.isObject() && result.isEmpty() ? "" : limit(result.toString(), maxCharacters);
  }

  private String limit(String value, int maxCharacters) {
    String normalized = value == null ? "" : value.strip();
    int limit = Math.max(1, maxCharacters);
    if (normalized.length() <= limit) {
      return normalized;
    }
    return normalized.substring(0, Math.max(0, limit - 3)) + "...";
  }

  private boolean directError(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return false;
    }
    if (node.isObject()) {
      JsonNode error = node.get("error");
      if (error != null && !error.isNull() && !error.asText().isBlank()) {
        return true;
      }
      JsonNode nestedResult = node.get("result");
      if (nestedResult != null && directError(nestedResult)) {
        return true;
      }
      JsonNode text = node.get("text");
      return text != null && directError(text);
    }
    if (node.isArray()) {
      for (JsonNode child : node) {
        if (directError(child)) {
          return true;
        }
      }
      return false;
    }
    if (!node.isTextual()) {
      return false;
    }
    String text = node.asText().strip();
    if (text.startsWith("Error:")) {
      return true;
    }
    try {
      JsonNode parsed = JSON.readTree(text);
      return parsed != null && !parsed.isTextual() && directError(parsed);
    } catch (Exception ignored) {
      return false;
    }
  }
}
