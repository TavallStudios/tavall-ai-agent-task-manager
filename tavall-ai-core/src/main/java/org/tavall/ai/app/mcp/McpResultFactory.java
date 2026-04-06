package org.tavall.ai.app.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.ResourceContents;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class McpResultFactory {

  private final ObjectMapper objectMapper;

  public McpResultFactory(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public CallToolResult toolResult(Object payload) {
    return new CallToolResult(List.of(textContent(payload)), false, payload, null);
  }

  public CallToolResult errorResult(String message) {
    return new CallToolResult(List.of(new TextContent(message)), true, null, null);
  }

  public ReadResourceResult resourceResult(ResourceContents resourceContents) {
    return new ReadResourceResult(List.of(resourceContents));
  }

  public GetPromptResult promptResult(String description, String body) {
    return new GetPromptResult(
        description,
        List.of(new PromptMessage(Role.USER, new TextContent(body)))
    );
  }

  private Content textContent(Object payload) {
    try {
      return new TextContent(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
    } catch (JsonProcessingException exception) {
      return new TextContent(String.valueOf(payload));
    }
  }
}

