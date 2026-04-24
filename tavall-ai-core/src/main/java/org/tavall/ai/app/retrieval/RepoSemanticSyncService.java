package org.tavall.ai.app.retrieval;

import org.tavall.ai.app.config.MemorySyncProperties;
import org.tavall.ai.app.config.SemanticIndexProperties;
import org.tavall.ai.app.harness.cleanjava.symbol.JavaSourceFileDiscoveryService;
import org.tavall.ai.app.harness.cleanjava.symbol.JavaSymbolSemanticIndexingService;
import org.tavall.ai.app.model.KnownRepo;
import org.tavall.ai.app.orchestration.GitWorktreeManager;
import org.tavall.ai.app.orchestration.SharedTaskContextService;
import org.tavall.ai.app.persistence.postgres.RepoSemanticSyncState;
import org.tavall.ai.app.persistence.postgres.RepoSemanticSyncStateRepository;
import org.tavall.ai.app.service.RepoCatalogService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class RepoSemanticSyncService {

  private final GitWorktreeManager gitWorktreeManager;
  private final JavaSourceFileDiscoveryService javaSourceFileDiscoveryService;
  private final JavaSymbolSemanticIndexingService javaSymbolSemanticIndexingService;
  private final MemorySyncProperties properties;
  private final RepoCatalogService repoCatalogService;
  private final RepoSemanticFileSupport repoSemanticFileSupport;
  private final RepoSemanticSyncStateRepository repoSemanticSyncStateRepository;
  private final SemanticIndexProperties semanticIndexProperties;
  private final SemanticSyncService semanticSyncService;
  private final SharedTaskContextService sharedTaskContextService;

  public RepoSemanticSyncService(
      GitWorktreeManager gitWorktreeManager,
      JavaSourceFileDiscoveryService javaSourceFileDiscoveryService,
      JavaSymbolSemanticIndexingService javaSymbolSemanticIndexingService,
      MemorySyncProperties properties,
      RepoCatalogService repoCatalogService,
      RepoSemanticFileSupport repoSemanticFileSupport,
      RepoSemanticSyncStateRepository repoSemanticSyncStateRepository,
      SemanticIndexProperties semanticIndexProperties,
      SemanticSyncService semanticSyncService,
      SharedTaskContextService sharedTaskContextService
  ) {
    this.gitWorktreeManager = gitWorktreeManager;
    this.javaSourceFileDiscoveryService = javaSourceFileDiscoveryService;
    this.javaSymbolSemanticIndexingService = javaSymbolSemanticIndexingService;
    this.properties = properties;
    this.repoCatalogService = repoCatalogService;
    this.repoSemanticFileSupport = repoSemanticFileSupport;
    this.repoSemanticSyncStateRepository = repoSemanticSyncStateRepository;
    this.semanticIndexProperties = semanticIndexProperties;
    this.semanticSyncService = semanticSyncService;
    this.sharedTaskContextService = sharedTaskContextService;
  }

  public int syncManagedRepos() {
    if (!properties.isEnabled() || !properties.isManagedRepoBackfillEnabled()) {
      return 0;
    }
    int completed = 0;
    for (KnownRepo repo : prioritizedRepos()) {
      if (completed >= properties.getMaxRepoBackfillsPerCycle()) {
        break;
      }
      if (needsBackfill(repo)) {
        syncRepo(repo);
        completed++;
      }
    }
    return completed;
  }

  public Map<String, Object> syncWorkspaceChanges(KnownRepo repo, Path workspacePath) {
    if (!properties.isEnabled() || !properties.isWorkspaceSyncEnabled()) {
      return Map.of("status", "disabled");
    }
    return applyWorkspaceChanges(repo, workspacePath, gitWorktreeManager.listWorkspaceChanges(workspacePath));
  }

  public Map<String, Object> reconcileWorkspaceChanges(KnownRepo repo, Path workspacePath, String baseRevision) {
    if (!properties.isEnabled() || !properties.isWorkspaceSyncEnabled()) {
      return Map.of("status", "disabled");
    }
    return applyWorkspaceChanges(repo, workspacePath, gitWorktreeManager.listWorkspaceChangesSince(workspacePath, baseRevision));
  }

  private Map<String, Object> applyWorkspaceChanges(
      KnownRepo repo,
      Path workspacePath,
      List<GitWorktreeManager.WorkspaceFileChange> changes
  ) {
    int deleted = 0;
    int upserted = 0;
    int upsertedJavaSymbols = 0;
    List<String> changedJavaSourcePaths = new java.util.ArrayList<>();
    for (GitWorktreeManager.WorkspaceFileChange change : changes) {
      String relativePath = normalizeRelativePath(change.relativePath());
      if (repoSemanticFileSupport.isExcludedRelativePath(relativePath)) {
        continue;
      }
      if (change.changeType() == GitWorktreeManager.WorkspaceFileChangeType.DELETE) {
        semanticSyncService.deleteProject(
            repo.projectKey(),
            Map.of("sourcePath", relativePath),
            repoSemanticFileSupport.deleteDedupeKey(repo, relativePath)
        );
        deleteJavaSymbolSourcePath(repo.projectKey(), relativePath);
        deleted++;
        continue;
      }
      Path target = workspacePath.resolve(relativePath);
      if (!repoSemanticFileSupport.isSyncCandidate(workspacePath, target)) {
        continue;
      }
      try {
        sharedTaskContextService.storeProjectSemanticDocument(
            repo.projectKey(),
            repoSemanticFileSupport.buildRequest(repo, workspacePath, target),
            repoSemanticFileSupport.upsertDedupeKey(repo, relativePath)
        );
        upserted++;
        if (isJavaSourcePath(relativePath)) {
          changedJavaSourcePaths.add(relativePath);
        }
      } catch (IOException exception) {
        repoSemanticSyncStateRepository.markScanFailure(
            repo.projectKey(),
            workspacePath.toString(),
            exception.getMessage()
          );
      }
    }
    if (!changedJavaSourcePaths.isEmpty()) {
      deleteJavaSymbolSourcePaths(repo.projectKey(), changedJavaSourcePaths);
      upsertedJavaSymbols = javaSymbolSemanticIndexingService.reconcileSourcePaths(
          repo.projectKey(),
          null,
          null,
          workspacePath,
          changedJavaSourcePaths,
          SemanticSyncMode.BACKGROUND_ONLY
      );
    }
    return Map.of(
        "status", "completed",
        "upsertedFiles", upserted,
        "deletedFiles", deleted,
        "upsertedJavaSymbols", upsertedJavaSymbols
    );
  }

  public Map<String, Object> loadStatus(String projectKey) {
    Optional<RepoSemanticSyncState> state = repoSemanticSyncStateRepository.find(projectKey);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("pendingSyncOperations", semanticSyncService.pendingCount());
    state.ifPresent(value -> {
      payload.put("repoPath", value.repoPath());
      payload.put("lastSyncedHead", value.lastSyncedHead());
      payload.put("lastSyncedAt", value.lastSyncedAt());
      payload.put("lastScanStartedAt", value.lastScanStartedAt());
      payload.put("lastScanCompletedAt", value.lastScanCompletedAt());
      payload.put("lastError", value.lastError());
    });
    if (payload.isEmpty()) {
      payload.put("pendingSyncOperations", semanticSyncService.pendingCount());
    }
    return payload;
  }

  private boolean needsBackfill(KnownRepo repo) {
    Optional<RepoSemanticSyncState> state = repoSemanticSyncStateRepository.find(repo.projectKey());
    if (state.isEmpty()) {
      return true;
    }
    if (!repo.repoPath().equals(state.get().repoPath())) {
      return true;
    }
    String currentHead = gitWorktreeManager.currentRevision(Path.of(repo.repoPath()));
    return !currentHead.equals(state.get().lastSyncedHead());
  }

  private void syncRepo(KnownRepo repo) {
    Path repoPath = Path.of(repo.repoPath());
    repoSemanticSyncStateRepository.markScanStarted(repo.projectKey(), repo.repoPath());
    try {
      semanticSyncService.deleteProject(
          repo.projectKey(),
          Map.of("semanticDomain", SemanticCollectionDomain.CODE_REPO.name()),
          repoSemanticFileSupport.domainDeleteDedupeKey(repo.projectKey(), SemanticCollectionDomain.CODE_REPO)
      );
      semanticSyncService.deleteProject(
          repo.projectKey(),
          Map.of("semanticDomain", SemanticCollectionDomain.KNOWLEDGE_RULES.name()),
          repoSemanticFileSupport.domainDeleteDedupeKey(repo.projectKey(), SemanticCollectionDomain.KNOWLEDGE_RULES)
      );
      try (Stream<Path> stream = Files.walk(repoPath)) {
        List<Path> files = stream
            .filter(file -> repoSemanticFileSupport.isSyncCandidate(repoPath, file))
            .sorted(Comparator.naturalOrder())
            .toList();
        for (Path file : files) {
          String relativePath = repoSemanticFileSupport.relativePath(repoPath, file);
          sharedTaskContextService.storeProjectSemanticDocument(
              repo.projectKey(),
              repoSemanticFileSupport.buildRequest(repo, repoPath, file),
            repoSemanticFileSupport.upsertDedupeKey(repo, relativePath)
          );
        }
      }
      javaSymbolSemanticIndexingService.indexRepositoryProfiles(
          repo.projectKey(),
          null,
          null,
          repoPath,
          SemanticSyncMode.BACKGROUND_ONLY
      );
      repoSemanticSyncStateRepository.markScanSuccess(
          repo.projectKey(),
          repo.repoPath(),
          gitWorktreeManager.currentRevision(repoPath)
      );
    } catch (IOException | RuntimeException exception) {
      repoSemanticSyncStateRepository.markScanFailure(
          repo.projectKey(),
          repo.repoPath(),
          exception.getMessage()
      );
    }
  }

  private List<KnownRepo> prioritizedRepos() {
    return repoCatalogService.listRepos().stream()
        .sorted(Comparator
            .comparingInt(this::priorityRank)
            .thenComparing(KnownRepo::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(KnownRepo::repoPath, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  private int priorityRank(KnownRepo repo) {
    List<String> prioritizedNames = semanticIndexProperties.getReindexRepoNames();
    for (int index = 0; index < prioritizedNames.size(); index++) {
      if (prioritizedNames.get(index).equalsIgnoreCase(repo.displayName())) {
        return index;
      }
    }
    return prioritizedNames.size();
  }

  private void deleteJavaSymbolSourcePaths(String projectKey, List<String> sourcePaths) {
    for (String sourcePath : sourcePaths.stream().map(this::normalizeRelativePath).distinct().toList()) {
      deleteJavaSymbolSourcePath(projectKey, sourcePath);
    }
  }

  private void deleteJavaSymbolSourcePath(String projectKey, String sourcePath) {
    if (!isJavaSourcePath(sourcePath)) {
      return;
    }
    sharedTaskContextService.deleteProjectSemanticContexts(
        projectKey,
        Map.of("javaSymbol", true, "sourcePath", sourcePath)
    );
  }

  private boolean isJavaSourcePath(String relativePath) {
    return !javaSourceFileDiscoveryService.filterJavaSourcePaths(List.of(relativePath)).isEmpty();
  }

  private String normalizeRelativePath(String relativePath) {
    return relativePath == null ? "" : relativePath.strip().replace('\\', '/');
  }
}

