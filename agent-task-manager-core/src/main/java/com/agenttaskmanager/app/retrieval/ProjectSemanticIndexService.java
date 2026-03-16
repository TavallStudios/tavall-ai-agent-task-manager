package com.agenttaskmanager.app.retrieval;

import com.agenttaskmanager.app.config.SemanticIndexProperties;
import com.agenttaskmanager.app.model.KnownRepo;
import com.agenttaskmanager.app.service.RepoCatalogService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ProjectSemanticIndexService {

  private final RepoCatalogService repoCatalogService;
  private final SemanticIndexProperties properties;
  private final SemanticVectorStoreService semanticVectorStoreService;

  public ProjectSemanticIndexService(
      RepoCatalogService repoCatalogService,
      SemanticIndexProperties properties,
      SemanticVectorStoreService semanticVectorStoreService
  ) {
    this.repoCatalogService = repoCatalogService;
    this.properties = properties;
    this.semanticVectorStoreService = semanticVectorStoreService;
  }

  public ProjectSemanticIndexSummary reindexConfiguredRepos() {
    List<KnownRepo> repos = repoCatalogService.listRepos().stream()
        .filter(repo -> properties.getReindexRepoNames().stream().anyMatch(name -> name.equalsIgnoreCase(repo.displayName())))
        .toList();
    List<RepoSemanticSummary> summaries = repos.stream()
        .map(this::reindexRepo)
        .toList();
    return new ProjectSemanticIndexSummary(repos.size(), summaries);
  }

  public RepoSemanticSummary reindexRepo(KnownRepo repo) {
    Path repoPath = Path.of(repo.repoPath());
    semanticVectorStoreService.deleteProject(repo.projectKey(), Map.of());
    int indexedDocs = 0;
    int indexedCodeFiles = 0;
    try (Stream<Path> stream = Files.walk(repoPath)) {
      List<Path> files = stream
          .filter(Files::isRegularFile)
          .filter(this::isIndexableFile)
          .sorted(Comparator.naturalOrder())
          .toList();
      for (Path file : files) {
        String relativePath = repoPath.relativize(file).toString().replace('\\', '/');
        String content = Files.readString(file, StandardCharsets.UTF_8);
        SemanticContentType contentType = classifyFile(relativePath);
        SemanticCollectionDomain domain = contentType == SemanticContentType.CODE
            ? SemanticCollectionDomain.CODE_REPO
            : SemanticCollectionDomain.KNOWLEDGE_RULES;
        SemanticDocumentRequest request = new SemanticDocumentRequest(
            SemanticVectorStoreService.deterministicDocumentId(repo.projectKey() + ":" + relativePath),
            null,
            null,
            "project-reindex",
            relativePath,
            content,
            domain,
            contentType,
            Map.of(
                "sourcePath", relativePath,
                "sourceRepo", repo.displayName(),
                "locationLabel", repo.locationLabel()
            )
        );
        semanticVectorStoreService.storeProjectDocument(repo.projectKey(), request);
        if (domain == SemanticCollectionDomain.CODE_REPO) {
          indexedCodeFiles++;
        } else {
          indexedDocs++;
        }
      }
      return new RepoSemanticSummary(repo.displayName(), repo.projectKey(), repo.repoPath(), indexedDocs, indexedCodeFiles);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to reindex semantic project context for " + repo.displayName(), exception);
    }
  }

  private boolean isIndexableFile(Path path) {
    String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return lower.endsWith(".java")
        || lower.endsWith(".kt")
        || lower.endsWith(".groovy")
        || lower.endsWith(".js")
        || lower.endsWith(".ts")
        || lower.endsWith(".tsx")
        || lower.endsWith(".jsx")
        || lower.endsWith(".py")
        || lower.endsWith(".cs")
        || lower.endsWith(".cpp")
        || lower.endsWith(".c")
        || lower.endsWith(".h")
        || lower.endsWith(".hpp")
        || lower.endsWith(".md")
        || lower.endsWith(".txt")
        || lower.endsWith(".json")
        || lower.endsWith(".yml")
        || lower.endsWith(".yaml")
        || lower.endsWith(".xml")
        || lower.endsWith(".properties");
  }

  private SemanticContentType classifyFile(String relativePath) {
    String lower = relativePath.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".md")
        || lower.endsWith(".txt")
        || lower.endsWith(".json")
        || lower.endsWith(".yml")
        || lower.endsWith(".yaml")
        || lower.endsWith(".xml")
        || lower.endsWith(".properties")) {
      return SemanticContentType.DOCUMENTATION;
    }
    return SemanticContentType.CODE;
  }

  public record ProjectSemanticIndexSummary(int reposReindexed, List<RepoSemanticSummary> repositories) {
  }

  public record RepoSemanticSummary(
      String displayName,
      String projectKey,
      String repoPath,
      int indexedDocs,
      int indexedCodeFiles
  ) {
    public Map<String, Object> asMap() {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("displayName", displayName);
      payload.put("projectKey", projectKey);
      payload.put("repoPath", repoPath);
      payload.put("indexedDocs", indexedDocs);
      payload.put("indexedCodeFiles", indexedCodeFiles);
      return payload;
    }
  }
}
