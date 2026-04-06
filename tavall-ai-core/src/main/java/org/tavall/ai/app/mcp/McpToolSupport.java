package org.tavall.ai.app.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Map;

public class McpToolSupport {

  protected final McpJsonSchemaFactory schemaFactory;

  public McpToolSupport(McpJsonSchemaFactory schemaFactory) {
    this.schemaFactory = schemaFactory;
  }

  protected Tool tool(String name, String description, Map<String, Object> properties, List<String> required) {
    return Tool.builder()
        .name(name)
        .description(description)
        .inputSchema(schemaFactory.objectSchema(properties, required))
        .build();
  }

  protected Tool tool(String name, String description, JsonSchema inputSchema) {
    return Tool.builder()
        .name(name)
        .description(description)
        .inputSchema(inputSchema)
        .build();
  }
}

