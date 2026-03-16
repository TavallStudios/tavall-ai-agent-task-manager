package com.agenttaskmanager.app.harness.cleanjava;

import com.agenttaskmanager.app.harness.state.HarnessStateService;
import com.agenttaskmanager.app.harness.state.HarnessStateSnapshot;
import com.agenttaskmanager.app.model.orchestration.ArtifactRecord;
import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import com.agenttaskmanager.app.model.orchestration.SharedTaskContext;
import com.agenttaskmanager.app.model.validation.ValidationReport;
import com.agenttaskmanager.app.orchestration.ArtifactService;
import com.agenttaskmanager.app.orchestration.SharedTaskContextService;
import com.agenttaskmanager.app.persistence.postgres.ValidationReportRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

@Service
public class CleanJavaTaskContextService {

  private final ArtifactService artifactService;
  private final HarnessStateService harnessStateService;
  private final JavaPackageDependencyMapService javaPackageDependencyMapService;
  private final SharedTaskContextService sharedTaskContextService;
  private final ValidationReportRepository validationReportRepository;

  public CleanJavaTaskContextService(
      ArtifactService artifactService,
      HarnessStateService harnessStateService,
      JavaPackageDependencyMapService javaPackageDependencyMapService,
      SharedTaskContextService sharedTaskContextService,
      ValidationReportRepository validationReportRepository
  ) {
    this.artifactService = artifactService;
    this.harnessStateService = harnessStateService;
    this.javaPackageDependencyMapService = javaPackageDependencyMapService;
    this.sharedTaskContextService = sharedTaskContextService;
    this.validationReportRepository = validationReportRepository;
  }

  public CleanJavaTaskContext buildContext(
      String taskId,
      String workerTaskId,
      String projectKey,
      Path repoPath,
      String queryText
  ) {
    Path normalizedRepoPath = repoPath.toAbsolutePath().normalize();
    HarnessStateSnapshot harnessState = loadHarnessState(taskId);
    String resolvedProjectKey = resolveProjectKey(projectKey, harnessState);
    String resolvedQueryText = resolveQueryText(queryText, harnessState);
    return new CleanJavaTaskContext(
        taskId == null ? "" : taskId,
        workerTaskId == null ? "" : workerTaskId,
        resolvedProjectKey,
        normalizedRepoPath.toString(),
        requestedTask(harnessState),
        resolvedQueryText,
        relevantFiles(harnessState),
        relevantDiffs(taskId, workerTaskId),
        readDoc(normalizedRepoPath, "RULES.md"),
        readDoc(normalizedRepoPath, "EXAMPLES.md"),
        readDoc(normalizedRepoPath, "ARCHITECTURE.md"),
        similarFixes(resolvedProjectKey, resolvedQueryText),
        javaPackageDependencyMapService.summarize(normalizedRepoPath),
        validationHistory(taskId),
        relevantArtifacts(taskId, workerTaskId),
        harnessState
    );
  }

  private HarnessStateSnapshot loadHarnessState(String taskId) {
    if (taskId == null || taskId.isBlank()) {
      return null;
    }
    return harnessStateService.loadState(taskId);
  }

  private String resolveProjectKey(String projectKey, HarnessStateSnapshot harnessState) {
    if (projectKey != null && !projectKey.isBlank()) {
      return projectKey.strip();
    }
    if (harnessState != null && harnessState.taskSchema() != null && harnessState.taskSchema().batch() != null) {
      return harnessState.taskSchema().batch().projectKey();
    }
    return "";
  }

  private String resolveQueryText(String queryText, HarnessStateSnapshot harnessState) {
    if (queryText != null && !queryText.isBlank()) {
      return queryText.strip();
    }
    if (harnessState != null && harnessState.taskSchema() != null && harnessState.taskSchema().batch() != null) {
      return harnessState.taskSchema().batch().title();
    }
    return "";
  }

  private String requestedTask(HarnessStateSnapshot harnessState) {
    if (harnessState == null || harnessState.taskSchema() == null || harnessState.taskSchema().batch() == null) {
      return "";
    }
    return harnessState.taskSchema().batch().title();
  }

  private List<String> relevantFiles(HarnessStateSnapshot harnessState) {
    if (harnessState == null || harnessState.taskSchema() == null) {
      return List.of();
    }
    TreeSet<String> files = new TreeSet<>();
    for (SharedTaskContext context : harnessState.taskSchema().sharedTaskContext()) {
      addFileValues(files, context.payload().get("changedFiles"));
      addFileValues(files, context.payload().get("filesChanged"));
      addFileValues(files, context.payload().get("sourcePath"));
    }
    return List.copyOf(files);
  }

  private List<Map<String, Object>> relevantDiffs(String taskId, String workerTaskId) {
    if (taskId == null || taskId.isBlank()) {
      return List.of();
    }
    return artifactService.loadTaskArtifacts(taskId, workerTaskId).stream()
        .filter(artifact -> "diff".equals(artifact.artifactKind()))
        .map(artifact -> Map.<String, Object>of(
            "artifactId", artifact.artifactId(),
            "summary", artifact.summary(),
            "body", artifactService.readArtifact(artifact.artifactId()).orElse("")
        ))
        .toList();
  }

  private List<RetrievedSemanticContext> similarFixes(String projectKey, String queryText) {
    if (projectKey.isBlank() || queryText.isBlank()) {
      return List.of();
    }
    return sharedTaskContextService.searchProjectRelatedContexts(projectKey, queryText, 6);
  }

  private List<ValidationReport> validationHistory(String taskId) {
    if (taskId == null || taskId.isBlank()) {
      return List.of();
    }
    return validationReportRepository.listReportsByTask(taskId).stream()
        .limit(5)
        .toList();
  }

  private List<Map<String, Object>> relevantArtifacts(String taskId, String workerTaskId) {
    if (taskId == null || taskId.isBlank()) {
      return List.of();
    }
    List<Map<String, Object>> artifacts = new ArrayList<>();
    for (ArtifactRecord artifact : artifactService.loadTaskArtifacts(taskId, workerTaskId)) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("artifactId", artifact.artifactId());
      payload.put("artifactKind", artifact.artifactKind());
      payload.put("summary", artifact.summary());
      payload.put("metadata", artifact.metadata());
      payload.put("body", snippet(artifactService.readArtifact(artifact.artifactId()).orElse(""), 800));
      artifacts.add(payload);
    }
    return List.copyOf(artifacts);
  }

  private void addFileValues(TreeSet<String> files, Object rawValue) {
    if (rawValue instanceof Iterable<?> values) {
      for (Object value : values) {
        addFileValues(files, value);
      }
      return;
    }
    if (rawValue != null) {
      String value = String.valueOf(rawValue).strip();
      if (!value.isBlank()) {
        files.add(value);
      }
    }
  }

  private String readDoc(Path repoPath, String fileName) {
    Path current = repoPath;
    while (current != null) {
      Path candidate = current.resolve(fileName);
      if (Files.isRegularFile(candidate)) {
        try {
          return Files.readString(candidate, StandardCharsets.UTF_8);
        } catch (IOException exception) {
          return "Failed to read " + fileName + ": " + exception.getMessage();
        }
      }
      current = current.getParent();
    }
    return "";
  }

  private String snippet(String value, int maxLength) {
    String normalized = value == null ? "" : value.replaceAll("\\s+", " ").strip();
    if (normalized.length() <= maxLength) {
      return normalized;
    }
    return normalized.substring(0, maxLength - 3) + "...";
  }
}
