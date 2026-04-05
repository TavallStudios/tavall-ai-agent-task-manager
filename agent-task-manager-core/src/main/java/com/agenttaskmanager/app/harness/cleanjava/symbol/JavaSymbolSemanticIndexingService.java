package com.agenttaskmanager.app.harness.cleanjava.symbol;

import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import com.agenttaskmanager.app.retrieval.SemanticCollectionDomain;
import com.agenttaskmanager.app.retrieval.SemanticContentType;
import com.agenttaskmanager.app.retrieval.SemanticDocumentRequest;
import com.agenttaskmanager.app.retrieval.SemanticSyncMode;
import com.agenttaskmanager.app.retrieval.SemanticVectorStoreService;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class JavaSymbolSemanticIndexingService {

  private static final String KIND = "java-symbol-profile";

  private final JavaSourceFileDiscoveryService sourceFileDiscoveryService;
  private final JavaSourceSymbolReader sourceSymbolReader;
  private final JavaSymbolSemanticDocumentRenderer renderer;
  private final SharedTaskContextService sharedTaskContextService;

  public JavaSymbolSemanticIndexingService(
      JavaSourceFileDiscoveryService sourceFileDiscoveryService,
      JavaSourceSymbolReader sourceSymbolReader,
      JavaSymbolSemanticDocumentRenderer renderer,
      SharedTaskContextService sharedTaskContextService
  ) {
    this.sourceFileDiscoveryService = sourceFileDiscoveryService;
    this.sourceSymbolReader = sourceSymbolReader;
    this.renderer = renderer;
    this.sharedTaskContextService = sharedTaskContextService;
  }

  public void queueProfiles(String projectKey, String taskId, String workerTaskId, List<JavaClassProfile> profiles) {
    indexProfiles(projectKey, taskId, workerTaskId, profiles, SemanticSyncMode.BACKGROUND_ONLY);
  }

  public int indexRepositoryProfiles(
      String projectKey,
      String taskId,
      String workerTaskId,
      Path repoPath,
      SemanticSyncMode mode
  ) {
    if (projectKey == null || projectKey.isBlank() || repoPath == null || !sourceFileDiscoveryService.hasJavaSources(repoPath)) {
      return 0;
    }
    JavaSourceSymbolCatalog catalog = sourceSymbolReader.readCatalog(repoPath);
    return indexProfiles(
        projectKey,
        taskId,
        workerTaskId,
        catalog.profilesByClassName().values().stream()
            .sorted(Comparator.comparing(JavaClassProfile::qualifiedName))
            .toList(),
        mode
    );
  }

  public int reconcileSourcePaths(
      String projectKey,
      String taskId,
      String workerTaskId,
      Path repoPath,
      List<String> sourcePaths,
      SemanticSyncMode mode
  ) {
    if (projectKey == null || projectKey.isBlank() || repoPath == null) {
      return 0;
    }
    List<String> changedJavaSourcePaths = sourceFileDiscoveryService.filterJavaSourcePaths(sourcePaths);
    if (changedJavaSourcePaths.isEmpty()) {
      return 0;
    }
    JavaSourceSymbolCatalog catalog = sourceSymbolReader.readCatalog(repoPath);
    LinkedHashSet<String> classNames = new LinkedHashSet<>();
    for (String sourcePath : changedJavaSourcePaths) {
      classNames.addAll(catalog.classesBySourcePath().getOrDefault(sourcePath, List.of()));
    }
    return indexProfiles(
        projectKey,
        taskId,
        workerTaskId,
        classNames.stream()
            .map(catalog.profilesByClassName()::get)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(JavaClassProfile::qualifiedName))
            .toList(),
        mode
    );
  }

  public int indexProfiles(
      String projectKey,
      String taskId,
      String workerTaskId,
      List<JavaClassProfile> profiles,
      SemanticSyncMode mode
  ) {
    if (projectKey == null || projectKey.isBlank() || profiles == null || profiles.isEmpty()) {
      return 0;
    }
    int indexed = 0;
    for (JavaClassProfile profile : profiles.stream().sorted(Comparator.comparing(JavaClassProfile::qualifiedName)).toList()) {
      SemanticDocumentRequest request = new SemanticDocumentRequest(
          documentId(projectKey, profile),
          blank(taskId),
          blank(workerTaskId),
          KIND,
          profile.qualifiedName(),
          renderer.render(profile),
          SemanticCollectionDomain.CODE_REPO,
          SemanticContentType.CODE,
          payload(profile)
      );
      if (mode == SemanticSyncMode.BACKGROUND_ONLY) {
        sharedTaskContextService.enqueueProjectSemanticDocument(projectKey, request, dedupeKey(projectKey, profile));
      } else {
        sharedTaskContextService.storeProjectSemanticDocument(projectKey, request, dedupeKey(projectKey, profile));
      }
      indexed++;
    }
    return indexed;
  }

  private Map<String, Object> payload(JavaClassProfile profile) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("sourcePath", profile.sourcePath());
    payload.put("className", profile.qualifiedName());
    payload.put("simpleName", profile.simpleName());
    payload.put("packageName", profile.packageName());
    payload.put("javaSymbol", true);
    payload.put("symbolKind", "class-profile");
    payload.put("javaMethodNames", profile.methods().stream().map(JavaMethodProfile::name).distinct().sorted().toList());
    payload.put("javaFieldNames", profile.fields().stream().map(JavaFieldProfile::name).sorted().toList());
    payload.put("javaReferenceTypes", profile.referencedTypes());
    payload.put("updatedAt", OffsetDateTime.now().toString());
    return payload;
  }

  private String documentId(String projectKey, JavaClassProfile profile) {
    return SemanticVectorStoreService.deterministicDocumentId(projectKey + ":java-symbol:" + profile.qualifiedName());
  }

  private String dedupeKey(String projectKey, JavaClassProfile profile) {
    return "java-symbol-profile:" + projectKey + ":" + profile.qualifiedName();
  }

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }
}
