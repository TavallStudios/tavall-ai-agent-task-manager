package com.agenttaskmanager.app.mcp;

import com.agenttaskmanager.app.config.McpServerProperties;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class McpCatalog {

  private final List<McpPromptProvider> promptProviders;
  private final List<McpResourceProvider> resourceProviders;
  private final List<McpToolProvider> toolProviders;
  private final McpInteractionMemoryService interactionMemoryService;
  private final McpServerProperties properties;

  public McpCatalog(
      List<McpPromptProvider> promptProviders,
      List<McpResourceProvider> resourceProviders,
      List<McpToolProvider> toolProviders,
      McpInteractionMemoryService interactionMemoryService,
      McpServerProperties properties
  ) {
    this.promptProviders = promptProviders;
    this.resourceProviders = resourceProviders;
    this.toolProviders = toolProviders;
    this.interactionMemoryService = interactionMemoryService;
    this.properties = properties;
  }

  public List<SyncToolSpecification> toolSpecifications() {
    Set<String> activeGroups = properties.getToolGroups().stream()
        .filter(group -> group != null && !group.isBlank())
        .collect(Collectors.toSet());
    return toolProviders.stream()
        .filter(provider -> activeGroups.isEmpty() || provider.serverGroups().stream().anyMatch(activeGroups::contains))
        .flatMap(provider -> provider.toolSpecifications().stream())
        .map(interactionMemoryService::wrapToolSpecification)
        .toList();
  }

  public List<SyncResourceSpecification> resourceSpecifications() {
    return resourceProviders.stream()
        .flatMap(provider -> provider.resourceSpecifications().stream())
        .map(interactionMemoryService::wrapResourceSpecification)
        .toList();
  }

  public List<SyncPromptSpecification> promptSpecifications() {
    return promptProviders.stream()
        .flatMap(provider -> provider.promptSpecifications().stream())
        .map(interactionMemoryService::wrapPromptSpecification)
        .toList();
  }
}
