package com.agenttaskmanager.app.mcp;

import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class McpJsonSchemaFactory {

  public JsonSchema objectSchema(Map<String, Object> properties, List<String> required) {
    return new JsonSchema("object", properties, required, Boolean.FALSE, Map.of(), Map.of());
  }

  public Map<String, Object> stringProperty(String description) {
    return Map.of("type", "string", "description", description);
  }

  public Map<String, Object> booleanProperty(String description) {
    return Map.of("type", "boolean", "description", description);
  }

  public Map<String, Object> integerProperty(String description) {
    return Map.of("type", "integer", "description", description);
  }

  public Map<String, Object> arrayProperty(String description, Map<String, Object> itemSchema) {
    Map<String, Object> property = new LinkedHashMap<>();
    property.put("type", "array");
    property.put("description", description);
    property.put("items", itemSchema);
    return property;
  }
}
