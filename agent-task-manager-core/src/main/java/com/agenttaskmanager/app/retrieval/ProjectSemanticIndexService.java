package com.agenttaskmanager.app.retrieval;

import com.agenttaskmanager.app.config.SemanticIndexProperties;
import com.agenttaskmanager.app.harness.cleanjava.symbol.JavaSymbolSemanticIndexingService;
import com.agenttaskmanager.app.model.KnownRepo;
import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import com.agenttaskmanager.app.service.RepoCatalogService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ProjectSemanticIndexService {

  private final RepoCatalogService repoCatalogService;
  private final JavaSymbolSemanticIndexingService javaSymbolSemanticIndexingService;
  private final SemanticIndexProperties properties;
  private final RepoSemanticFileSupport repoSemanticFileSupport;
  private final SemanticSyncService semanticSyncService;
  private final SharedTaskContextService sharedTaskContextService;

  public ProjectSemanticIndexService(
      RepoCatalogService repoCatalogService,
      JavaSymbolSemanticIndexingService javaSymbolSemanticIndexingService,
      SemanticIndexProperties properties,
      RepoSemanticFileSupport repoSemanticFileSupport,
      SemanticSyncService semanticSyncService,
      SharedTaskContextService sharedTaskContextService
  ) {
    this.repoCatalogService = repoCatalogService;
    this.javaSymbolSemanticIndexingService = javaSymbolSemanticIndexingService;
    this.properties = properties;
    this.repoSemanticFileSupport = repoSemanticFileSupport;
    this.semanticSyncService = semanticSyncService;
    this.sharedTaskContextService = sharedTaskContextService;
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
    semanticSyncService.deleteProject(repo.projectKey(), Map.of("semanticDomain", SemanticCollectionDomain.CODE_REPO.name()), null);
    semanticSyncService.deleteProject(repo.projectKey(), Map.of("semanticDomain", SemanticCollectionDomain.KNOWLEDGE_RULES.name()), null);
    int indexedDocs = 0;
    int indexedCodeFiles = 0;
    int indexedJavaSymbols = 0;
    try (Stream<Path> stream = Files.walk(repoPath)) {
      List<Path> files = stream
          .filter(Files::isRegularFile)
          .filter(repoSemanticFileSupport::isIndexable)
          .sorted(Comparator.naturalOrder())
          .toList();
      for (Path file : files) {
        String relativePath = repoSemanticFileSupport.relativePath(repoPath, file);
        SemanticDocumentRequest request = repoSemanticFileSupport.buildRequest(repo, repoPath, file);
        sharedTaskContextService.storeProjectSemanticDocument(
            repo.projectKey(),
            request,
            repoSemanticFileSupport.upsertDedupeKey(repo, relativePath)
        );
        if (request.domain() == SemanticCollectionDomain.CODE_REPO) {
          indexedCodeFiles++;
        } else {
          indexedDocs++;
        }
      }
      indexedJavaSymbols = javaSymbolSemanticIndexingService.indexRepositoryProfiles(
          repo.projectKey(),
          null,
          null,
          repoPath,
          SemanticSyncMode.WRITE_THROUGH
      );
      return new RepoSemanticSummary(repo.displayName(), repo.projectKey(), repo.repoPath(), indexedDocs, indexedCodeFiles, indexedJavaSymbols);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to reindex semantic project context for " + repo.displayName(), exception);
    }
  }

  public record ProjectSemanticIndexSummary(int reposReindexed, List<RepoSemanticSummary> repositories) {
  }

  public record RepoSemanticSummary(
      String displayName,
      String projectKey,
      String repoPath,
      int indexedDocs,
      int indexedCodeFiles,
      int indexedJavaSymbols
  ) {
    public Map<String, Object> asMap() {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("displayName", displayName);
      payload.put("projectKey", projectKey);
      payload.put("repoPath", repoPath);
      payload.put("indexedDocs", indexedDocs);
      payload.put("indexedCodeFiles", indexedCodeFiles);
      payload.put("indexedJavaSymbols", indexedJavaSymbols);
      return payload;
    }
  }
}
