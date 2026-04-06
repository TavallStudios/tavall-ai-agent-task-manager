package org.tavall.ai.app.mcp.tools.repo;

import org.tavall.ai.app.harness.tools.SharedRepoSnapshotService;
import org.tavall.ai.app.mcp.McpJsonSchemaFactory;
import org.tavall.ai.app.mcp.McpResultFactory;
import org.tavall.ai.app.mcp.McpToolPayloadMapper;
import org.tavall.ai.app.mcp.McpToolProvider;
import org.tavall.ai.app.mcp.McpToolSupport;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SharedRepoSnapshotToolHandler extends McpToolSupport implements McpToolProvider {

  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;
  private final SharedRepoSnapshotService sharedRepoSnapshotService;

  public SharedRepoSnapshotToolHandler(
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper,
      SharedRepoSnapshotService sharedRepoSnapshotService
  ) {
    super(schemaFactory);
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
    this.sharedRepoSnapshotService = sharedRepoSnapshotService;
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        spec(
            "stageSharedRepoSnapshot",
            "Decode and stage an uploaded repo snapshot into a local temporary workspace.",
            Map.of(
                "repoName", stringProperty("Repository display name."),
                "archiveBase64", stringProperty("ZIP archive encoded as base64.")
            ),
            List.of("archiveBase64"),
            arguments -> {
              StageSharedRepoSnapshotRequest request = map(arguments, StageSharedRepoSnapshotRequest.class);
              return new StageSharedRepoSnapshotResponse(
                  sharedRepoSnapshotService.stageArchive(request.repoName(), request.archiveBase64()).toString()
              );
            }
        )
    );
  }

  private SyncToolSpecification spec(
      String name,
      String description,
      Map<String, Object> properties,
      List<String> required,
      ToolCall call
  ) {
    return new SyncToolSpecification(
        tool(name, description, properties, required),
        (exchange, request) -> resultFactory.toolResult(call.run(request.arguments()))
    );
  }

  private <T> T map(Map<String, Object> arguments, Class<T> type) {
    return payloadMapper.map(arguments, type);
  }

  private Map<String, Object> stringProperty(String description) {
    return schemaFactory.stringProperty(description);
  }

  @FunctionalInterface
  private interface ToolCall {
    Object run(Map<String, Object> arguments);
  }
}

