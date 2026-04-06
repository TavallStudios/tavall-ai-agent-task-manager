package org.tavall.ai.app.mcp.tools.artifact;

import org.tavall.ai.app.mcp.McpJsonSchemaFactory;
import org.tavall.ai.app.mcp.McpResultFactory;
import org.tavall.ai.app.mcp.McpToolPayloadMapper;
import org.tavall.ai.app.mcp.McpToolProvider;
import org.tavall.ai.app.mcp.McpToolSupport;
import org.tavall.ai.app.orchestration.ArtifactService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ArtifactToolHandler extends McpToolSupport implements McpToolProvider {

  private final ArtifactService artifactService;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public ArtifactToolHandler(
      ArtifactService artifactService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.artifactService = artifactService;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        spec(
            "readArtifact",
            "Read an artifact body.",
            Map.of("artifactId", stringProperty("Artifact id.")),
            List.of("artifactId"),
            arguments -> new ArtifactBodyResponse(artifactService.readArtifact(map(arguments, ArtifactIdRequest.class).artifactId()).orElse(""))
        ),
        spec(
            "writeArtifact",
            "Write a generic artifact.",
            Map.of(
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "artifactKind", stringProperty("Artifact kind."),
                "summary", stringProperty("Summary."),
                "body", stringProperty("Artifact body."),
                "metadata", Map.of("type", "object", "description", "Metadata.")
            ),
            List.of("taskId", "artifactKind", "summary", "body"),
            arguments -> writeArtifact(arguments, true)
        ),
        spec(
            "storeTaskArtifact",
            "Store a task artifact.",
            Map.of(
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "artifactKind", stringProperty("Artifact kind."),
                "summary", stringProperty("Summary."),
                "body", stringProperty("Artifact body.")
            ),
            List.of("taskId", "artifactKind", "summary", "body"),
            arguments -> writeArtifact(arguments, false)
        ),
        spec(
            "loadTaskArtifacts",
            "Load task artifacts.",
            Map.of("taskId", stringProperty("Task id."), "workerTaskId", stringProperty("Worker task id.")),
            List.of("taskId"),
            arguments -> {
              LoadArtifactsRequest request = map(arguments, LoadArtifactsRequest.class);
              return new ArtifactListResponse(artifactService.loadTaskArtifacts(request.taskId(), request.workerTaskId()));
            }
        ),
        spec(
            "storeDiffArtifact",
            "Store a diff artifact.",
            Map.of(
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "diffBody", stringProperty("Diff body.")
            ),
            List.of("taskId", "workerTaskId", "diffBody"),
            arguments -> {
              StoreDiffArtifactRequest request = map(arguments, StoreDiffArtifactRequest.class);
              return new ArtifactRecordResponse(artifactService.storeDiffArtifact(
                  request.taskId(),
                  request.workerTaskId(),
                  request.diffBody(),
                  Map.of()
              ));
            }
        ),
        spec(
            "loadDiffArtifact",
            "Load a diff artifact body.",
            Map.of("artifactId", stringProperty("Artifact id.")),
            List.of("artifactId"),
            arguments -> new ArtifactBodyResponse(artifactService.readArtifact(map(arguments, ArtifactIdRequest.class).artifactId()).orElse(""))
        )
    );
  }

  private ArtifactRecordResponse writeArtifact(Map<String, Object> arguments, boolean withMetadata) {
    WriteArtifactRequest request = map(arguments, WriteArtifactRequest.class);
    return new ArtifactRecordResponse(artifactService.writeArtifact(
        request.taskId(),
        request.workerTaskId(),
        request.artifactKind(),
        request.summary(),
        request.body(),
        withMetadata && request.metadata() != null ? request.metadata() : Map.of()
    ));
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

