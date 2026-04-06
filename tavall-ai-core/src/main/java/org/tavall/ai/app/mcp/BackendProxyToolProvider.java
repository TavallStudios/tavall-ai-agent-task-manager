package org.tavall.ai.app.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class BackendProxyToolProvider extends McpToolSupport implements McpToolProvider {
  private static final Set<String> BLOCKED_GIT_MUTATION_TOOLS = Set.of(
      "git_add",
      "git_checkout",
      "git_commit",
      "git_create_branch",
      "git_reset"
  );

  private final BackendConnectorRegistryService backendConnectorRegistryService;
  private final DownstreamMcpToolClientService downstreamMcpToolClientService;
  private final McpResultFactory resultFactory;

  public BackendProxyToolProvider(
      BackendConnectorRegistryService backendConnectorRegistryService,
      DownstreamMcpToolClientService downstreamMcpToolClientService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory
  ) {
    super(schemaFactory);
    this.backendConnectorRegistryService = backendConnectorRegistryService;
    this.downstreamMcpToolClientService = downstreamMcpToolClientService;
    this.resultFactory = resultFactory;
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return backendConnectorRegistryService.enabledConnectors().stream()
        .flatMap(connector -> connector.toolCache().stream()
            .filter(tool -> shouldExposeTool(connector, tool))
            .map(tool -> specification(connector, tool)))
        .toList();
  }

  private boolean shouldExposeTool(BackendConnectorDefinition connector, BackendToolDefinition tool) {
    return !("git".equalsIgnoreCase(connector.id())
        && BLOCKED_GIT_MUTATION_TOOLS.contains(normalizeToolName(tool.name())));
  }

  private String normalizeToolName(String toolName) {
    return toolName == null ? "" : toolName.strip().toLowerCase().replace(' ', '_');
  }

  private SyncToolSpecification specification(
      BackendConnectorDefinition connector,
      BackendToolDefinition tool
  ) {
    String namespacedName = connector.id() + "." + tool.name();
    String description = tool.summary() == null || tool.summary().isBlank()
        ? "Proxy tool exposed by the " + connector.resolvedDisplayName() + " backend connector."
        : tool.summary();
    return new SyncToolSpecification(
        tool(namespacedName, description, schemaFactory.openObjectSchema()),
        (exchange, request) -> invoke(() -> callBackendTool(namespacedName, request.arguments()))
    );
  }

  private Object callBackendTool(String namespacedName, Map<String, Object> arguments) {
    BackendConnectorRegistryService.ResolvedBackendTool resolved = backendConnectorRegistryService.resolveTool(namespacedName)
        .orElseThrow(() -> new IllegalArgumentException("Unknown backend proxy tool: " + namespacedName));
    DownstreamMcpToolResult result = downstreamMcpToolClientService.callTool(
        null,
        new DownstreamMcpToolCall(
            namespacedName,
            resolved.connector().id(),
            resolved.tool().name(),
            arguments == null ? Map.of() : arguments
        )
    );
    if (result.isError()) {
      throw new IllegalStateException(result.errorMessage() == null ? "Backend proxy tool failed." : result.errorMessage());
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("backendId", resolved.connector().id());
    payload.put("backendDisplayName", resolved.connector().resolvedDisplayName());
    payload.put("toolName", resolved.tool().name());
    payload.put("status", result.status());
    payload.put("structuredContent", result.structuredContent());
    payload.put("textContent", result.textContent());
    payload.put("stderr", result.stderr());
    payload.put("durationMs", result.durationMs());
    return payload;
  }

  private io.modelcontextprotocol.spec.McpSchema.CallToolResult invoke(Supplier<Object> supplier) {
    try {
      return resultFactory.toolResult(supplier.get());
    } catch (IllegalArgumentException | IllegalStateException exception) {
      return resultFactory.errorResult(exception.getMessage());
    }
  }
}

