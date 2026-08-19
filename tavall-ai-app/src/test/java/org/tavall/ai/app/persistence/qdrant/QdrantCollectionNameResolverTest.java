package org.tavall.ai.app.persistence.qdrant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.tavall.ai.app.config.EmbeddingProperties;
import org.tavall.ai.app.config.QdrantProperties;
import org.tavall.ai.app.retrieval.SemanticCollectionDomain;
import java.util.List;
import org.junit.jupiter.api.Test;

class QdrantCollectionNameResolverTest {

  @Test
  void shouldIsolateLocalCollectionsByEmbeddingProfile() {
    QdrantCollectionNameResolver resolver = resolver(new EmbeddingProperties());

    assertEquals(
        "agent_task_manager_project_project_novus_tasks__local_baai_bge_small_en_v1_5_384",
        resolver.projectCollection("Project Novus", SemanticCollectionDomain.TASK_HISTORY)
    );
    assertEquals(
        "agent_task_manager_knowledge_architecture__local_baai_bge_small_en_v1_5_384",
        resolver.knowledgeCollection("Architecture")
    );
  }

  @Test
  void shouldKeepLegacyCollectionUnsuffixedForMigrationCompatibility() {
    QdrantProperties qdrantProperties = new QdrantProperties();
    qdrantProperties.setCollection("agent_task_manager_context_v2");
    QdrantCollectionNameResolver resolver = new QdrantCollectionNameResolver(
        qdrantProperties,
        new EmbeddingProperties()
    );

    assertEquals("agent_task_manager_context_v2", resolver.legacyCollection());
  }

  @Test
  void shouldChangeCollectionProfileWhenEmbeddingConfigurationChanges() {
    EmbeddingProperties embeddingProperties = new EmbeddingProperties();
    embeddingProperties.setProviderOrder(List.of("gemini", "local"));
    embeddingProperties.setGeminiModel("gemini-embedding-2-preview");
    embeddingProperties.setDimensions(1536);
    QdrantCollectionNameResolver resolver = resolver(embeddingProperties);

    assertEquals(
        "agent_task_manager_project_tavall_ai__gemini_gemini_embedding_2_preview_1536",
        resolver.projectCollection("tavall-ai")
    );
  }

  private QdrantCollectionNameResolver resolver(EmbeddingProperties embeddingProperties) {
    return new QdrantCollectionNameResolver(new QdrantProperties(), embeddingProperties);
  }
}
