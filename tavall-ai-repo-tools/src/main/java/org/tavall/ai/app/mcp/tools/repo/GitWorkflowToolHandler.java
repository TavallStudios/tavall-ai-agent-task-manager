package org.tavall.ai.app.mcp.tools.repo;

import org.tavall.ai.app.mcp.McpJsonSchemaFactory;
import org.tavall.ai.app.mcp.McpResultFactory;
import org.tavall.ai.app.mcp.McpToolPayloadMapper;
import org.tavall.ai.app.mcp.McpToolProvider;
import org.tavall.ai.app.mcp.McpToolSupport;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class GitWorkflowToolHandler extends McpToolSupport implements McpToolProvider {

  private final GitWorkflowService gitWorkflowService;
  private final McpResultFactory resultFactory;
  private final McpToolPayloadMapper payloadMapper;

  public GitWorkflowToolHandler(
      GitWorkflowService gitWorkflowService,
      McpJsonSchemaFactory schemaFactory,
      McpResultFactory resultFactory,
      McpToolPayloadMapper payloadMapper
  ) {
    super(schemaFactory);
    this.gitWorkflowService = gitWorkflowService;
    this.resultFactory = resultFactory;
    this.payloadMapper = payloadMapper;
  }

  @Override
  public List<SyncToolSpecification> toolSpecifications() {
    return List.of(
        specification("planGitCommit", "Plan a concern-scoped branch and verbose commit without mutating git state.", this::planCommit),
        specification("prepareGitBranch", "Create or switch to the deterministic domain-system-user-vN branch for the current concern.", this::prepareBranch),
        specification("createGitCommit", "Create a verbose concern-scoped commit through the first-party git workflow.", this::createCommit)
    );
  }

  private Object planCommit(Map<String, Object> arguments) {
    return gitWorkflowService.plan(map(arguments));
  }

  private Object prepareBranch(Map<String, Object> arguments) {
    return gitWorkflowService.prepareBranch(map(arguments));
  }

  private Object createCommit(Map<String, Object> arguments) {
    return gitWorkflowService.createCommit(map(arguments));
  }

  private SyncToolSpecification specification(String name, String description, ToolCall call) {
    return new SyncToolSpecification(
        tool(name, description, properties(), requiredFields()),
        (exchange, request) -> invoke(() -> call.run(request.arguments()))
    );
  }

  private io.modelcontextprotocol.spec.McpSchema.CallToolResult invoke(Supplier<Object> supplier) {
    try {
      return resultFactory.toolResult(supplier.get());
    } catch (IllegalArgumentException | IllegalStateException exception) {
      return resultFactory.errorResult(exception.getMessage());
    }
  }

  private GitWorkflowRequest map(Map<String, Object> arguments) {
    return payloadMapper.map(arguments, GitWorkflowRequest.class);
  }

  private Map<String, Object> properties() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("repoPath", stringProperty("Absolute or repo-relative path to the git repository."));
    properties.put("changeType", stringProperty("One of Added, Changed, Fix, Refactor, or Removed."));
    properties.put("domain", stringProperty("Primary domain segment for the branch name."));
    properties.put("system", stringProperty("Primary system segment for the branch name."));
    properties.put("user", stringProperty("Primary user/operator segment for the branch name."));
    properties.put("version", stringProperty("Version segment for the branch name, usually vN."));
    properties.put("summary", stringProperty("Short subject summary rendered after the change-type prefix."));
    properties.put("details", stringProperty("Verbose what-changed section content for the commit body."));
    properties.put("verification", stringProperty("Verification section content for the commit body."));
    properties.put("finalChange", booleanProperty("Whether this concern is final enough to allow Fix or Refactor."));
    properties.put("allowMixedDomain", booleanProperty("Allow a grouped commit even when files span multiple concerns."));
    properties.put("filePaths", arrayProperty("Optional concern-scoped file paths to stage and commit.", stringProperty("Relative file path.")));
    properties.put("domainOverride", stringProperty("Optional override for the rendered domain segment."));
    properties.put("systemOverride", stringProperty("Optional override for the rendered system segment."));
    properties.put("userOverride", stringProperty("Optional override for the rendered user segment."));
    properties.put("versionOverride", stringProperty("Optional override for the rendered version segment."));
    return properties;
  }

  private List<String> requiredFields() {
    return List.of("repoPath", "changeType", "domain", "system", "user", "version", "summary", "details", "verification");
  }

  private Map<String, Object> stringProperty(String description) {
    return schemaFactory.stringProperty(description);
  }

  private Map<String, Object> booleanProperty(String description) {
    return schemaFactory.booleanProperty(description);
  }

  private Map<String, Object> arrayProperty(String description, Map<String, Object> itemSchema) {
    return schemaFactory.arrayProperty(description, itemSchema);
  }

  @FunctionalInterface
  private interface ToolCall {
    Object run(Map<String, Object> arguments);
  }
}

