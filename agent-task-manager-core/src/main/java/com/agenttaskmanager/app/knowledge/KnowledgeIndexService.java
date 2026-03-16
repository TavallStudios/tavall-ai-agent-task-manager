package com.agenttaskmanager.app.knowledge;

import com.agenttaskmanager.app.config.KnowledgeIndexProperties;
import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeIndexService {

  private static final String KNOWLEDGE_KIND = "knowledge-index";
  private final KnowledgeIndexProperties properties;
  private final KnowledgeChunker chunker;
  private final SharedTaskContextService sharedTaskContextService;

  public KnowledgeIndexService(
      KnowledgeIndexProperties properties,
      KnowledgeChunker chunker,
      SharedTaskContextService sharedTaskContextService
  ) {
    this.properties = properties;
    this.chunker = chunker;
    this.sharedTaskContextService = sharedTaskContextService;
  }

  public KnowledgeIndexSummary reindex() {
    if (!properties.isEnabled()) {
      return new KnowledgeIndexSummary(false, 0, 0, "disabled");
    }
    sharedTaskContextService.deleteKnowledgeContexts(properties.getKnowledgeBase(), Map.of());
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
    return sharedTaskContextService.searchKnowledgeContexts(properties.getKnowledgeBase(), queryText, limit);
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
        indexedChunks += indexChunks("source-root", sourcePath, Files.readString(file, StandardCharsets.UTF_8));
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
      int indexedChunks = indexChunks("jar-entry", jarPath.getFileName().toString(), entries);
      return new KnowledgeIndexSummary(true, 1, indexedChunks, "jar-entry");
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to index configured knowledge jar entries.", exception);
    }
  }

  private int indexChunks(String sourceKind, String sourcePath, String content) {
    int indexedChunks = 0;
    for (KnowledgeChunk chunk : chunker.chunk(sourcePath, content)) {
      sharedTaskContextService.upsertKnowledgeContext(
          properties.getKnowledgeBase(),
          deterministicPointId(chunk.sourcePath(), chunk.chunkIndex()),
          KNOWLEDGE_KIND,
          chunk.text(),
          Map.of(
              "knowledgeBase", properties.getKnowledgeBase(),
              "sourceKind", sourceKind,
              "sourcePath", chunk.sourcePath(),
              "chunkIndex", chunk.chunkIndex(),
              "startLine", chunk.startLine(),
              "endLine", chunk.endLine()
          )
      );
      indexedChunks++;
    }
    return indexedChunks;
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

  private String deterministicPointId(String sourcePath, int chunkIndex) {
    String key = properties.getKnowledgeBase() + ":" + sourcePath + ":" + chunkIndex;
    return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
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
