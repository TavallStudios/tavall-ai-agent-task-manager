package org.tavall.ai.app.mcp.cleanjava;

import org.tavall.ai.app.harness.cleanjava.CleanJavaTaskContext;
import org.tavall.ai.app.harness.cleanjava.CleanJavaTaskContextService;
import org.tavall.ai.app.mcp.McpJsonSchemaFactory;
import org.tavall.ai.app.mcp.McpResultFactory;
import org.tavall.ai.app.mcp.McpToolPayloadMapper;
import org.tavall.ai.app.mcp.McpToolProvider;
import org.tavall.ai.app.mcp.McpToolSupport;
import org.tavall.ai.app.validation.ValidationPipelineService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class CleanJavaMcpTools extends McpToolSupport implements McpToolProvider {

  private final CleanJavaTaskContextService cleanJavaTaskContextService;
  private final ValidationPipelineService validationPipelineService;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public CleanJavaMcpTools(
      CleanJavaTaskContextService cleanJavaTaskContextService,
      ValidationPipelineService validationPipelineService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.cleanJavaTaskContextService = cleanJavaTaskContextService;
    this.validationPipelineService = validationPipelineService;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  @Override
  public List<String> serverGroups() {
    return List.of("clean-java-mcp");
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        spec("loadCleanJavaRules", "Load AgentTaskManager clean Java rules.", Map.of(), List.of(), arguments -> new CleanJavaRulesResponse(readDoc("RULES.md"))),
        spec(
            "loadCleanJavaMcpTaskContext",
            "Build deterministic clean Java task context with rules, examples, semantic recall, package dependencies, and current validation state.",
            Map.of(
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "projectKey", stringProperty("Project key for semantic retrieval."),
                "repoPath", stringProperty("Repo path."),
                "queryText", stringProperty("Optional retrieval query override.")
            ),
            List.of("repoPath"),
            arguments -> new CleanJavaMcpTaskContextResponse(loadTaskContext(arguments))
        ),
        spec(
            "runCleanJavaArchUnit",
            "Run ArchUnit clean Java rules against the requested repository root.",
            Map.of(
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "repoPath", stringProperty("Repo path.")
            ),
            List.of("taskId", "repoPath"),
            arguments -> {
              CleanJavaRepoRequest request = map(arguments, CleanJavaRepoRequest.class);
              return validationPipelineService.runArchUnitValidation(
                  request.taskId(),
                  request.workerTaskId(),
                  Path.of(request.repoPath())
              );
            }
        ),
        spec(
            "runCleanJavaSpoon",
            "Run Spoon clean Java source-shape rules.",
            Map.of(
                "taskId", stringProperty("Task id."),
                "workerTaskId", stringProperty("Worker task id."),
                "repoPath", stringProperty("Repo path.")
            ),
            List.of("taskId", "repoPath"),
            arguments -> {
              CleanJavaRepoRequest request = map(arguments, CleanJavaRepoRequest.class);
              return validationPipelineService.runSpoonValidation(request.taskId(), request.workerTaskId(), Path.of(request.repoPath()));
            }
        ),
        spec(
            "validateCleanJavaPatchScope",
            "Validate clean Java patch scope.",
            Map.of("diffBody", stringProperty("Diff body.")),
            List.of("diffBody"),
            arguments -> new CleanJavaPatchScopeResponse(validationPipelineService.validatePatchScope(
                map(arguments, CleanJavaPatchScopeRequest.class).diffBody()
            ))
        )
    );
  }

  private CleanJavaTaskContext loadTaskContext(Map<String, Object> arguments) {
    CleanJavaContextRequest request = map(arguments, CleanJavaContextRequest.class);
    return cleanJavaTaskContextService.buildContext(
        request.taskId(),
        request.workerTaskId(),
        request.projectKey(),
        Path.of(request.repoPath()),
        request.queryText()
    );
  }

  private SyncToolSpecification spec(String name, String description, Map<String, Object> properties, List<String> required, ToolCall call) {
    return new SyncToolSpecification(tool(name, description, properties, required), (exchange, request) -> resultFactory.toolResult(call.run(request.arguments())));
  }

  private <T> T map(Map<String, Object> arguments, Class<T> type) {
    return payloadMapper.map(arguments, type);
  }

  private Map<String, Object> stringProperty(String description) {
    return schemaFactory.stringProperty(description);
  }

  private String readDoc(String fileName) {
    try {
      return Files.readString(resolveDocPath(fileName), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      return "Failed to read " + fileName + ": " + exception.getMessage();
    }
  }

  public static Path resolveDocPath(String fileName) throws IOException {
    return resolveDocPath(defaultSearchRoots(), fileName);
  }

  public static Path resolveDocPath(List<Path> searchRoots, String fileName) throws IOException {
    for (Path searchRoot : searchRoots) {
      Path resolved = searchUpwardsForDoc(searchRoot, fileName);
      if (resolved != null) {
        return resolved;
      }
    }
    throw new IOException("Unable to locate " + fileName + " from search roots " + searchRoots);
  }

  private static List<Path> defaultSearchRoots() {
    return List.of(
        Path.of(".").toAbsolutePath().normalize(),
        codeSourceSearchRoot()
    );
  }

  private static Path codeSourceSearchRoot() {
    try {
      Path location = Path.of(CleanJavaMcpTools.class.getProtectionDomain().getCodeSource().getLocation().toURI())
          .toAbsolutePath()
          .normalize();
      return Files.isDirectory(location) ? location : location.getParent();
    } catch (URISyntaxException | NullPointerException exception) {
      return Path.of(".").toAbsolutePath().normalize();
    }
  }

  private static Path searchUpwardsForDoc(Path start, String fileName) {
    Path current = normalizeSearchRoot(start);
    while (current != null) {
      Path candidate = current.resolve(fileName);
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
      current = current.getParent();
    }
    return null;
  }

  private static Path normalizeSearchRoot(Path path) {
    if (path == null) {
      return null;
    }
    Path normalized = path.toAbsolutePath().normalize();
    return Files.isDirectory(normalized) ? normalized : normalized.getParent();
  }

  @FunctionalInterface
  private interface ToolCall {
    Object run(Map<String, Object> arguments);
  }
}

record CleanJavaContextRequest(
    String taskId,
    String workerTaskId,
    String projectKey,
    String repoPath,
    String queryText
) {
}

record CleanJavaRepoRequest(String taskId, String workerTaskId, String repoPath) {
}

record CleanJavaPatchScopeRequest(String diffBody) {
}

record CleanJavaRulesResponse(String body) {
}

record CleanJavaMcpTaskContextResponse(CleanJavaTaskContext taskContext) {
}

record CleanJavaPatchScopeResponse(boolean allowed) {
}

