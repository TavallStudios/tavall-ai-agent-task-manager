package org.tavall.ai.app.knowledge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;
import org.tavall.ai.app.support.IntegrationTestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "app.qdrant.collection=agent_task_manager_knowledge_index_test",
    "app.embedding.provider-order=hash",
    "app.embedding.dimensions=32",
    "app.semantic-index.max-chunk-chars=600",
    "app.semantic-index.code-target-chunk-lines=20",
    "app.semantic-index.doc-target-chunk-lines=20",
    "app.semantic-index.overlap-lines=5",
    "app.knowledge-index.enabled=true",
    "app.knowledge-index.knowledge-base=knowledge-test",
    "app.knowledge-index.prompt-result-limit=2"
})
class KnowledgeIndexServiceTest extends IntegrationTestSupport {

  private static final Path KNOWLEDGE_ROOT = Path.of(System.getProperty("java.io.tmpdir"), "tavall-ai-knowledge-it");
  private static final Path SAMPLE_FILE = KNOWLEDGE_ROOT.resolve("ExamplePacket.java");

  @Autowired
  private KnowledgeIndexService knowledgeIndexService;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("app.knowledge-index.source-root", () -> KNOWLEDGE_ROOT.toString());
    registry.add("app.knowledge-index.jar-path", () -> KNOWLEDGE_ROOT.resolve("knowledge-index.jar").toString());
  }

  @BeforeAll
  static void setUpKnowledgeRoot() throws IOException {
    Files.createDirectories(KNOWLEDGE_ROOT);
    Files.writeString(
        SAMPLE_FILE,
        "package test;\npublic class ExamplePacket {\n  void applyTimeoutCleanup() {\n    String body = \"disconnect cleanup payload\";\n  }\n}\n",
        StandardCharsets.UTF_8
    );
  }

  @Test
  void shouldIndexAndSearchConfiguredKnowledge() {
    KnowledgeIndexService.KnowledgeIndexSummary summary = knowledgeIndexService.reindex();
    List<RetrievedSemanticContext> results = knowledgeIndexService.search("disconnect cleanup payload", 5);

    assertTrue(summary.enabled());
    assertTrue(summary.indexedFiles() >= 1);
    assertTrue(summary.indexedChunks() >= 1);
    assertTrue(results.stream().anyMatch(item -> "knowledge-test".equals(item.payload().get("knowledgeBase"))));
    assertTrue(results.stream().anyMatch(item -> String.valueOf(item.payload().get("sourcePath")).contains("ExamplePacket.java")));
    assertTrue(results.stream().anyMatch(item -> "CODE".equals(item.payload().get("contentType"))));
    assertTrue(results.stream().anyMatch(item -> String.valueOf(item.payload().get("chunkText")).contains("disconnect cleanup payload")));
  }
}


