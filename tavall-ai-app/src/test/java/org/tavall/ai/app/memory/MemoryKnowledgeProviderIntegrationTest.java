package org.tavall.ai.app.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tavall.ai.app.config.MemoryRuntimeProperties;

class MemoryKnowledgeProviderIntegrationTest {

  @Test
  void shouldCallGraphifyWithBoundedStructuralQueryAndCaptureEvidence() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    try (MemoryMcpTestServer server = new MemoryMcpTestServer(objectMapper)) {
      server.setToolResultText("FFARuntimeService -> FFAFeatureBootstrap at src/main/java/F.java:42-51");
      MemoryRuntimeProperties properties = properties(server.endpoint());
      GraphifyCodeKnowledgeProvider provider = new GraphifyCodeKnowledgeProvider(
          properties,
          new MemoryMcpToolClient(objectMapper),
          new MemoryMcpResultReader()
      );

      MemoryKnowledgeContext context = provider.retrieve(new MemoryKnowledgeQuery(
          "tavall-project-novus",
          "/srv/workspace/tavall-project-novus",
          "what depends on FFA runtime",
          5,
          Map.of()
      ));

      JsonNode call = server.lastToolCall();
      assertEquals("query_graph", call.path("params").path("name").asText());
      assertEquals(3, call.path("params").path("arguments").path("depth").asInt());
      assertEquals(1600, call.path("params").path("arguments").path("token_budget").asInt());
      assertTrue(context.content().contains("FFARuntimeService"));
      assertTrue(context.evidenceReferences().contains("src/main/java/F.java:42-51"));
      assertFalse(context.degraded());

      provider.inspectPullRequest(
          "TavallStudios/tavall-project-novus",
          391,
          "/srv/workspace/tavall-project-novus"
      );
      JsonNode impactCall = server.lastToolCall();
      assertEquals("get_pr_impact", impactCall.path("params").path("name").asText());
      assertEquals(391, impactCall.path("params").path("arguments").path("pr_number").asInt());
      assertEquals(
          "TavallStudios/tavall-project-novus",
          impactCall.path("params").path("arguments").path("repo").asText()
      );
    }
  }

  @Test
  void shouldSearchAndWriteGraphitiFactsWithoutExtraction() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    try (MemoryMcpTestServer server = new MemoryMcpTestServer(objectMapper)) {
      MemoryRuntimeProperties properties = properties(server.endpoint());
      GraphitiTemporalKnowledgeProvider provider = new GraphitiTemporalKnowledgeProvider(
          properties,
          new MemoryMcpToolClient(objectMapper),
          new MemoryMcpResultReader()
      );

      provider.retrieve(new MemoryKnowledgeQuery(
          "tavall-ai",
          "",
          "what replaced the previous embedding default",
          4,
          Map.of()
      ));
      JsonNode searchCall = server.lastToolCall();
      assertEquals("search_memory_facts", searchCall.path("params").path("name").asText());
      assertEquals("tavall", searchCall.path("params").path("arguments").path("group_ids").get(0).asText());

      MemoryKnowledgeContext write = provider.recordTriplet(
          "repo://TavallStudios/tavall-ai-agent-task-manager",
          "USES_EMBEDDING_PROFILE",
          "Local BGE small 384-dimensional embeddings are the default semantic profile.",
          "embedding://BAAI/bge-small-en-v1.5/384"
      );
      JsonNode tripletCall = server.lastToolCall();
      assertEquals("add_triplet", tripletCall.path("params").path("name").asText());
      assertEquals("tavall", tripletCall.path("params").path("arguments").path("group_id").asText());
      assertFalse(write.degraded());
    }
  }

  @Test
  void shouldReportConfiguredProviderFailureAsDegraded() {
    ObjectMapper objectMapper = new ObjectMapper();
    MemoryRuntimeProperties properties = new MemoryRuntimeProperties();
    properties.setGraphifyMcpEndpoint("http://127.0.0.1:1/mcp");
    properties.setExternalProviderTimeout(Duration.ofMillis(200));
    GraphifyCodeKnowledgeProvider provider = new GraphifyCodeKnowledgeProvider(
        properties,
        new MemoryMcpToolClient(objectMapper),
        new MemoryMcpResultReader()
    );

    MemoryKnowledgeContext context = provider.retrieve(
        new MemoryKnowledgeQuery("project", "/srv/project", "impact", 3, Map.of())
    );

    assertTrue(context.degraded());
    assertFalse(context.error().isBlank());
  }

  @Test
  void shouldNotTreatGraphifyTextualErrorAsSuccessfulContext() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    try (MemoryMcpTestServer server = new MemoryMcpTestServer(objectMapper)) {
      server.setToolResultText("PR #391: no changed files found (may require gh auth).");
      GraphifyCodeKnowledgeProvider provider = new GraphifyCodeKnowledgeProvider(
          properties(server.endpoint()),
          new MemoryMcpToolClient(objectMapper),
          new MemoryMcpResultReader()
      );

      MemoryKnowledgeContext context = provider.inspectPullRequest(
          "TavallStudios/tavall-project-novus",
          391,
          "/srv/workspace/tavall-project-novus"
      );

      assertTrue(context.degraded());
      assertFalse(context.error().isBlank());
    }
  }

  @Test
  void shouldNotTreatSerializedGraphitiErrorAsSuccessfulContext() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    try (MemoryMcpTestServer server = new MemoryMcpTestServer(objectMapper)) {
      server.setToolResultText("{\"error\":\"Graphiti unavailable\"}");
      GraphitiTemporalKnowledgeProvider provider = new GraphitiTemporalKnowledgeProvider(
          properties(server.endpoint()),
          new MemoryMcpToolClient(objectMapper),
          new MemoryMcpResultReader()
      );

      MemoryKnowledgeContext context = provider.retrieve(new MemoryKnowledgeQuery(
          "tavall-ai",
          "",
          "what replaced the previous embedding default",
          4,
          Map.of()
      ));

      assertTrue(context.degraded());
      assertFalse(context.error().isBlank());
    }
  }

  @Test
  void shouldNotTreatNestedProviderMetadataAsMcpError() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    try (MemoryMcpTestServer server = new MemoryMcpTestServer(objectMapper)) {
      server.setToolResultText("{\"facts\":[{\"metadata\":{\"error\":\"historical field\"}}],\"message\":\"Facts retrieved\"}");
      GraphitiTemporalKnowledgeProvider provider = new GraphitiTemporalKnowledgeProvider(
          properties(server.endpoint()),
          new MemoryMcpToolClient(objectMapper),
          new MemoryMcpResultReader()
      );

      MemoryKnowledgeContext context = provider.retrieve(new MemoryKnowledgeQuery(
          "tavall-ai",
          "",
          "historical field",
          4,
          Map.of()
      ));

      assertFalse(context.degraded());
      assertTrue(context.content().contains("Facts retrieved"));
    }
  }

  private MemoryRuntimeProperties properties(String endpoint) {
    MemoryRuntimeProperties properties = new MemoryRuntimeProperties();
    properties.setGraphifyMcpEndpoint(endpoint);
    properties.setGraphitiMcpEndpoint(endpoint);
    properties.setGraphitiGroupId("tavall");
    properties.setExternalProviderTimeout(Duration.ofSeconds(2));
    return properties;
  }
}
