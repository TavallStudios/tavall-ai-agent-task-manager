package org.tavall.ai.app.memory;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.tavall.ai.app.config.MemoryRuntimeProperties;

@Component
public class GraphifyCodeKnowledgeProvider implements MemoryKnowledgeProvider {

  private static final Pattern SOURCE_REFERENCE = Pattern.compile(
      "(?<![A-Za-z0-9_.-])([A-Za-z0-9_./\\\\-]+\\.[A-Za-z0-9]+):(\\d+)(?:-(\\d+))?"
  );

  private final MemoryRuntimeProperties properties;
  private final MemoryMcpToolClient toolClient;
  private final MemoryMcpResultReader resultReader;

  public GraphifyCodeKnowledgeProvider(
      MemoryRuntimeProperties properties,
      MemoryMcpToolClient toolClient,
      MemoryMcpResultReader resultReader
  ) {
    this.properties = properties;
    this.toolClient = toolClient;
    this.resultReader = resultReader;
  }

  @Override
  public String providerId() {
    return "graphify";
  }

  @Override
  public MemoryKnowledgeRole role() {
    return MemoryKnowledgeRole.STRUCTURAL;
  }

  /** Retrieves current code topology from the configured Graphify MCP graph. */
  @Override
  public MemoryKnowledgeContext retrieve(MemoryKnowledgeQuery query) {
    if (!configured()) {
      return MemoryKnowledgeContext.disabled(providerId(), role());
    }
    long started = System.nanoTime();
    try {
      Map<String, Object> arguments = new LinkedHashMap<>();
      arguments.put("question", query.queryText());
      arguments.put("mode", "bfs");
      arguments.put("depth", Math.min(6, Math.max(1, properties.getGraphifyDepth())));
      arguments.put("token_budget", Math.max(256, properties.getGraphifyTokenBudget()));
      addProjectPath(arguments, query.repoPath());
      return call("query_graph", arguments, started);
    } catch (RuntimeException exception) {
      return MemoryKnowledgeContext.failed(providerId(), role(), elapsedMillis(started), exception);
    }
  }

  /** Retrieves Graphify's graph blast-radius analysis for one GitHub pull request. */
  public MemoryKnowledgeContext inspectPullRequest(String repo, int pullRequestNumber, String repoPath) {
    if (!configured()) {
      return MemoryKnowledgeContext.disabled(providerId(), role());
    }
    if (pullRequestNumber < 1) {
      throw new IllegalArgumentException("pullRequestNumber must be positive.");
    }
    long started = System.nanoTime();
    try {
      Map<String, Object> arguments = new LinkedHashMap<>();
      arguments.put("pr_number", pullRequestNumber);
      if (repo != null && !repo.isBlank()) {
        arguments.put("repo", repo.strip());
      }
      addProjectPath(arguments, repoPath);
      return call("get_pr_impact", arguments, started);
    } catch (RuntimeException exception) {
      return MemoryKnowledgeContext.failed(providerId(), role(), elapsedMillis(started), exception);
    }
  }

  private MemoryKnowledgeContext call(String toolName, Map<String, Object> arguments, long started) {
    JsonNode result = toolClient.callTool(
        properties.getGraphifyMcpEndpoint(),
        properties.getGraphifyApiKey(),
        properties.getExternalProviderTimeout(),
        toolName,
        arguments
    );
    if (resultReader.hasError(result)) {
      throw new IllegalStateException("Graphify returned an error result for tool " + toolName + ".");
    }
    String content = resultReader.text(result, maxCharacters());
    if (content.isBlank() || looksLikeProviderFailure(content, toolName)) {
      throw new IllegalStateException("Graphify returned no context for tool " + toolName + ".");
    }
    return new MemoryKnowledgeContext(
        providerId(),
        role(),
        content,
        sourceReferences(content),
        Map.of("configured", true, "tool", toolName),
        elapsedMillis(started),
        false,
        ""
    );
  }

  private int maxCharacters() {
    return Math.max(1024, properties.getGraphifyTokenBudget() * 4);
  }

  private boolean looksLikeProviderFailure(String content, String toolName) {
    String normalized = content.strip().toLowerCase(Locale.ROOT);
    return normalized.startsWith("error:")
        || ("get_pr_impact".equals(toolName) && normalized.contains("no changed files found"));
  }

  private void addProjectPath(Map<String, Object> arguments, String repoPath) {
    if (repoPath != null && !repoPath.isBlank()) {
      arguments.put("project_path", repoPath.strip());
    }
  }

  private boolean configured() {
    return properties.getGraphifyMcpEndpoint() != null && !properties.getGraphifyMcpEndpoint().isBlank();
  }

  private List<String> sourceReferences(String content) {
    if (content == null || content.isBlank()) {
      return List.of();
    }
    List<String> references = new ArrayList<>();
    Matcher matcher = SOURCE_REFERENCE.matcher(content);
    while (matcher.find() && references.size() < properties.getExternalContextLimit()) {
      String reference = matcher.group();
      if (!references.contains(reference)) {
        references.add(reference);
      }
    }
    return List.copyOf(references);
  }

  private long elapsedMillis(long started) {
    return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
  }
}
