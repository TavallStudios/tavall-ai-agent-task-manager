package org.tavall.ai.app.knowledge;

import org.tavall.ai.app.config.KnowledgeIndexProperties;
import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;
import org.tavall.ai.app.retrieval.SemanticContentType;
import org.tavall.ai.app.retrieval.SemanticMemoryService;
import org.tavall.ai.app.retrieval.SemanticVectorStoreService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeIndexService {

  private static final String KNOWLEDGE_KIND = "knowledge-index";
  private final KnowledgeIndexProperties properties;
  private final SemanticMemoryService semanticMemoryService;

  public KnowledgeIndexService(
      KnowledgeIndexProperties properties,
      SemanticMemoryService semanticMemoryService
  ) {
    this.properties = properties;
    this.semanticMemoryService = semanticMemoryService;
  }

  public KnowledgeIndexSummary reindex() {
    if (!properties.isEnabled()) {
      return new KnowledgeIndexSummary(false, 0, 0, "disabled");
    }
    semanticMemoryService.deleteKnowledgeContexts(properties.getKnowledgeBase(), Map.of());
    Path sourceRoot = resolvePath(properties.getSourceRoot());
    if (sourceRoot != null && Files.isDirectory(sourceRoot)) {
      return indexSourceFiles(sourceRoot);
    }
    Path jarPath = resolvePath(properties.getJarPath());
    if (jarPath != null && Files.isRegularFile(jarPath)) {
      return indexJarEntries(jarPath);
    }
    return new KnowledgeIndexSummary(true, 0, 0, "missing-source");
  }

  public List<RetrievedSemanticContext> search(String queryText, int limit) {
    return semanticMemoryService.searchKnowledge(properties.getKnowledgeBase(), queryText, limit, Map.of());
  }

  private KnowledgeIndexSummary indexSourceFiles(Path root) {
    int indexedFiles = 0;
    int indexedChunks = 0;
    try (Stream<Path> stream = Files.walk(root)) {
      List<Path> files = stream.filter(Files::isRegularFile)
          .filter(this::isKnowledgeFile)
          .sorted(Comparator.naturalOrder())
          .toList();
      for (Path file : files) {
        indexedFiles++;
        String sourcePath = root.relativize(file).toString().replace('\\', '/');
        indexedChunks += indexDocument("source-root", sourcePath, Files.readString(file, StandardCharsets.UTF_8));
      }
      return new KnowledgeIndexSummary(true, indexedFiles, indexedChunks, "source-root");
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to index configured knowledge source files.", exception);
    }
  }

  private KnowledgeIndexSummary indexJarEntries(Path jarPath) {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      String entries = jarFile.stream()
          .filter(entry -> !entry.isDirectory())
          .map(entry -> entry.getName())
          .sorted()
          .reduce((left, right) -> left + "\n" + right)
          .orElse("");
      int indexedChunks = indexDocument("jar-entry", jarPath.getFileName().toString(), entries);
      return new KnowledgeIndexSummary(true, 1, indexedChunks, "jar-entry");
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to index configured knowledge jar entries.", exception);
    }
  }

  private int indexDocument(String sourceKind, String sourcePath, String content) {
    return semanticMemoryService.storeKnowledgeDocument(
        properties.getKnowledgeBase(),
        SemanticVectorStoreService.deterministicDocumentId(properties.getKnowledgeBase() + ":" + sourcePath),
        KNOWLEDGE_KIND,
        sourcePath,
        content,
        classifyContentType(sourcePath),
        Map.of(
            "knowledgeBase", properties.getKnowledgeBase(),
            "sourceKind", sourceKind,
            "sourcePath", sourcePath,
            "contentType", classifyContentType(sourcePath).name()
        )
    ).size();
  }

  private SemanticContentType classifyContentType(String sourcePath) {
    String lower = sourcePath.toLowerCase();
    if (lower.endsWith(".java") || lower.endsWith(".kt")) {
      return SemanticContentType.CODE;
    }
    return SemanticContentType.DOCUMENTATION;
  }

  private boolean isKnowledgeFile(Path path) {
    String name = path.getFileName().toString().toLowerCase();
    return name.endsWith(".java")
        || name.endsWith(".kt")
        || name.endsWith(".txt")
        || name.endsWith(".json")
        || name.endsWith(".yml")
        || name.endsWith(".yaml")
        || name.endsWith(".xml")
        || name.endsWith(".properties")
        || name.endsWith(".md");
  }

  private static Path resolvePath(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Path.of(value);
  }

  public record KnowledgeIndexSummary(boolean enabled, int indexedFiles, int indexedChunks, String sourceKind) {
  }
}
