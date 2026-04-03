package com.agenttaskmanager.app.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DocumentationResourceProvider implements McpResourceProvider {

  private static final List<String> RESOURCE_FILES = List.of(
      "AGENTS.md",
      "RULES.md",
      "ARCHITECTURE.md",
      "EXAMPLES.md",
      "GIT_WORKFLOW.md",
      "README.md"
  );

  private final McpResultFactory mcpResultFactory;

  public DocumentationResourceProvider(McpResultFactory mcpResultFactory) {
    this.mcpResultFactory = mcpResultFactory;
  }

  @Override
  public List<SyncResourceSpecification> resourceSpecifications() {
    return RESOURCE_FILES.stream()
        .map(this::resourceSpecification)
        .toList();
  }

  private SyncResourceSpecification resourceSpecification(String fileName) {
    String uri = "file:///" + fileName;
    Resource resource = Resource.builder()
        .uri(uri)
        .name(fileName)
        .description("AgentTaskManager documentation resource: " + fileName)
        .mimeType("text/markdown")
        .build();
    return new SyncResourceSpecification(resource, (exchange, request) -> readResource(fileName, uri));
  }

  private ReadResourceResult readResource(String fileName, String uri) {
    Path path = Path.of(fileName);
    try {
      String body = Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
      return mcpResultFactory.resourceResult(new TextResourceContents(uri, "text/markdown", body));
    } catch (IOException exception) {
      return mcpResultFactory.resourceResult(
          new TextResourceContents(uri, "text/plain", "Failed to read " + fileName + ": " + exception.getMessage())
      );
    }
  }
}
