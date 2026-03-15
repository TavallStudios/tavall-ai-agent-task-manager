package com.agenttaskmanager.app.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class McpToolPayloadMapper {

  private final ObjectMapper objectMapper;

  public McpToolPayloadMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public <T> T map(Map<String, Object> payload, Class<T> type) {
    return objectMapper.convertValue(payload == null ? Map.of() : payload, type);
  }
}
