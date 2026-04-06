package org.tavall.ai.app.persistence.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonSupport {

  private final ObjectMapper objectMapper;

  public JsonSupport(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to serialize JSON payload.", exception);
    }
  }

  public Map<String, Object> readMap(String value) {
    return read(value, new TypeReference<>() {
    });
  }

  public List<String> readStringList(String value) {
    return read(value, new TypeReference<>() {
    });
  }

  public <T> T read(String value, TypeReference<T> typeReference) {
    try {
      return objectMapper.readValue(value == null || value.isBlank() ? "{}" : value, typeReference);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to deserialize JSON payload.", exception);
    }
  }
}

