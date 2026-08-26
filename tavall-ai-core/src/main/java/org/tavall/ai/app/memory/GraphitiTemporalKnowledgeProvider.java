package org.tavall.ai.app.memory;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.tavall.ai.app.config.MemoryRuntimeProperties;

@Component
public class GraphitiTemporalKnowledgeProvider implements MemoryKnowledgeProvider {

  private final MemoryRuntimeProperties properties;
  private final MemoryMcpToolClient toolClient;
  private final MemoryMcpResultReader resultReader;

  public GraphitiTemporalKnowledgeProvider(
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
    return "graphiti";
  }

  @Override
  public MemoryKnowledgeRole role() {
    return MemoryKnowledgeRole.TEMPORAL;
  }

  /** Retrieves evolving facts and historical relationships from Graphiti. */
  @Override
  public MemoryKnowledgeContext retrieve(MemoryKnowledgeQuery query) {
    if (!configured()) {
      return MemoryKnowledgeContext.disabled(providerId(), role());
    }
    long started = System.nanoTime();
    try {
      Map<String, Object> arguments = new LinkedHashMap<>();
      arguments.put("query", query.queryText());
      arguments.put("max_facts", Math.max(1, Math.min(query.limit(), properties.getExternalContextLimit())));
      if (properties.getGraphitiGroupId() != null && !properties.getGraphitiGroupId().isBlank()) {
        arguments.put("group_ids", List.of(properties.getGraphitiGroupId().strip()));
      }
      JsonNode result = toolClient.callTool(
          properties.getGraphitiMcpEndpoint(),
          properties.getGraphitiApiKey(),
          properties.getExternalProviderTimeout(),
          "search_memory_facts",
          arguments
      );
      if (resultReader.hasError(result)) {
        throw new IllegalStateException("Graphiti returned an error result.");
      }
      String content = resultReader.text(result, maxCharacters());
      if (content.isBlank()) {
        throw new IllegalStateException("Graphiti returned no temporal context.");
      }
      return new MemoryKnowledgeContext(
          providerId(),
          role(),
          content,
          List.of(),
          Map.of(
              "configured", true,
              "groupId", normalizedGroupId(),
              "maxFacts", arguments.get("max_facts")
          ),
          elapsedMillis(started),
          false,
          ""
      );
    } catch (RuntimeException exception) {
      return MemoryKnowledgeContext.failed(providerId(), role(), elapsedMillis(started), exception);
    }
  }

  /** Writes one already-verified relationship without asking Graphiti to extract it from prose. */
  public MemoryKnowledgeContext recordTriplet(
      String sourceNode,
      String edgeName,
      String fact,
      String targetNode
  ) {
    if (!configured()) {
      return MemoryKnowledgeContext.disabled(providerId(), role());
    }
    requireText(sourceNode, "sourceNode");
    requireText(edgeName, "edgeName");
    requireText(fact, "fact");
    requireText(targetNode, "targetNode");
    long started = System.nanoTime();
    try {
      Map<String, Object> arguments = new LinkedHashMap<>();
      arguments.put("source_node_name", sourceNode.strip());
      arguments.put("edge_name", edgeName.strip());
      arguments.put("fact", fact.strip());
      arguments.put("target_node_name", targetNode.strip());
      arguments.put("group_id", normalizedGroupId());
      JsonNode result = toolClient.callTool(
          properties.getGraphitiMcpEndpoint(),
          properties.getGraphitiApiKey(),
          properties.getExternalProviderTimeout(),
          "add_triplet",
          arguments
      );
      if (resultReader.hasError(result)) {
        throw new IllegalStateException("Graphiti returned an error result for add_triplet.");
      }
      String content = resultReader.text(result, maxCharacters());
      if (content.isBlank()) {
        throw new IllegalStateException("Graphiti returned no result for add_triplet.");
      }
      return new MemoryKnowledgeContext(
          providerId(),
          role(),
          content,
          List.of(),
          Map.of("configured", true, "groupId", normalizedGroupId(), "writeMode", "triplet"),
          elapsedMillis(started),
          false,
          ""
      );
    } catch (RuntimeException exception) {
      return MemoryKnowledgeContext.failed(providerId(), role(), elapsedMillis(started), exception);
    }
  }

  private boolean configured() {
    return properties.getGraphitiMcpEndpoint() != null && !properties.getGraphitiMcpEndpoint().isBlank();
  }

  private String normalizedGroupId() {
    String groupId = properties.getGraphitiGroupId();
    return groupId == null || groupId.isBlank() ? "tavall" : groupId.strip();
  }

  private int maxCharacters() {
    return Math.max(1024, properties.getExternalContextLimit() * 2000);
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required.");
    }
  }

  private long elapsedMillis(long started) {
    return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
  }
}
