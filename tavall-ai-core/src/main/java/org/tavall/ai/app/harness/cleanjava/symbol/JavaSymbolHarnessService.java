package org.tavall.ai.app.harness.cleanjava.symbol;

import cache.CacheDomain;
import cache.CacheSource;
import cache.CacheType;
import cache.JavaContractDeltaCache;
import cache.JavaSymbolNeighborhoodCache;
import cache.JavaSymbolProfileCache;
import org.tavall.ai.app.model.validation.ValidationReport;
import org.tavall.ai.app.orchestration.ArtifactService;
import org.tavall.ai.app.persistence.mongo.JavaSymbolDocumentStore;
import org.tavall.ai.app.persistence.postgres.ValidationReportRepository;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class JavaSymbolHarnessService {

  private final ArtifactService artifactService;
  private final JavaCompiledArtifactResolver compiledArtifactResolver;
  private final JavaContractDeltaCache contractDeltaCache;
  private final JavaContractDeltaService contractDeltaService;
  private final JavaReflectionAugmentationService reflectionAugmentationService;
  private final JavaSymbolSemanticIndexingService javaSymbolSemanticIndexingService;
  private final JavaSourceFileDiscoveryService sourceFileDiscoveryService;
  private final JavaSourceSymbolReader sourceSymbolReader;
  private final JavaSymbolDocumentStore javaSymbolDocumentStore;
  private final JavaSymbolNeighborhoodBuilder neighborhoodBuilder;
  private final JavaSymbolNeighborhoodCache neighborhoodCache;
  private final JavaSymbolProfileCache profileCache;
  private final JavaSymbolPromptRenderer promptRenderer;
  private final ValidationReportRepository validationReportRepository;

  public JavaSymbolHarnessService(
      ArtifactService artifactService,
      JavaCompiledArtifactResolver compiledArtifactResolver,
      JavaContractDeltaCache contractDeltaCache,
      JavaContractDeltaService contractDeltaService,
      JavaReflectionAugmentationService reflectionAugmentationService,
      JavaSymbolSemanticIndexingService javaSymbolSemanticIndexingService,
      JavaSourceFileDiscoveryService sourceFileDiscoveryService,
      JavaSourceSymbolReader sourceSymbolReader,
      JavaSymbolDocumentStore javaSymbolDocumentStore,
      JavaSymbolNeighborhoodBuilder neighborhoodBuilder,
      JavaSymbolNeighborhoodCache neighborhoodCache,
      JavaSymbolProfileCache profileCache,
      JavaSymbolPromptRenderer promptRenderer,
      ValidationReportRepository validationReportRepository
  ) {
    this.artifactService = artifactService;
    this.compiledArtifactResolver = compiledArtifactResolver;
    this.contractDeltaCache = contractDeltaCache;
    this.contractDeltaService = contractDeltaService;
    this.reflectionAugmentationService = reflectionAugmentationService;
    this.javaSymbolSemanticIndexingService = javaSymbolSemanticIndexingService;
    this.sourceFileDiscoveryService = sourceFileDiscoveryService;
    this.sourceSymbolReader = sourceSymbolReader;
    this.javaSymbolDocumentStore = javaSymbolDocumentStore;
    this.neighborhoodBuilder = neighborhoodBuilder;
    this.neighborhoodCache = neighborhoodCache;
    this.profileCache = profileCache;
    this.promptRenderer = promptRenderer;
    this.validationReportRepository = validationReportRepository;
  }

  public JavaSymbolBaseline captureBaseline(
      String correlationId,
      String taskId,
      String workerTaskId,
      String projectKey,
      Path repoPath,
      String promptText,
      String baseRevision,
      List<String> hintSourcePaths,
      List<String> currentChangedSourcePaths
  ) {
    if (!sourceFileDiscoveryService.hasJavaSources(repoPath)) {
      return skippedBaseline(correlationId, baseRevision);
    }
    try {
      JavaSourceSymbolCatalog catalog = sourceSymbolReader.readCatalog(repoPath);
      JavaSymbolNeighborhood neighborhood = neighborhoodBuilder.buildNeighborhood(
          catalog,
          hintSourcePaths,
          promptText,
          currentChangedSourcePaths
      );
      JavaReflectionAugmentationResult reflection = reflectionAugmentationService.augment(
          neighborhood.orderedProfiles(),
          compiledArtifactResolver.existingClasspathRoots(repoPath)
      );
      List<String> warnings = new ArrayList<>(mergeWarnings(neighborhood.warnings(), reflection.warnings()));
      JavaSymbolRunContext runContext = promptRenderer.buildRunContext(
          neighborhood,
          warnings,
          reflection.augmented(),
          recentValidationSummaries(taskId)
      );
      recordBaseline(correlationId, projectKey, taskId, workerTaskId, repoPath, baseRevision, catalog, neighborhood, reflection, warnings);
      return new JavaSymbolBaseline(
          correlationId,
          reflection.augmented() ? "captured" : "source-only",
          true,
          reflection.augmented(),
          baseRevision,
          catalog,
          neighborhood.anchorSourcePaths(),
          neighborhood,
          runContext,
          List.copyOf(warnings)
      );
    } catch (RuntimeException exception) {
      return failedBaseline(correlationId, baseRevision, exception.getMessage());
    }
  }

  public JavaSymbolRunContext buildRunContext(JavaSymbolBaseline baseline) {
    return baseline == null ? promptRenderer.buildRunContext(null, List.of(), false, List.of()) : baseline.runContext();
  }

  public JavaSymbolPostEditResult capturePostEdit(
      String correlationId,
      String taskId,
      String workerTaskId,
      String projectKey,
      Path repoPath,
      JavaSymbolBaseline baseline,
      List<String> changedSourcePaths
  ) {
    List<String> changedJavaSourcePaths = sourceFileDiscoveryService.filterJavaSourcePaths(changedSourcePaths);
    if (changedJavaSourcePaths.isEmpty()) {
      return new JavaSymbolPostEditResult(
          "skipped-no-java-diff",
          false,
          List.of(),
          List.of(),
          contractDeltaService.skipped("skipped-no-java-diff", List.of(), "No Java files changed during the run."),
          List.of(),
          "",
          ""
      );
    }
    try {
      JavaSourceSymbolCatalog currentCatalog = sourceSymbolReader.readCatalog(repoPath);
      JavaSymbolNeighborhood currentNeighborhood = neighborhoodBuilder.buildNeighborhood(
          currentCatalog,
          changedJavaSourcePaths,
          "",
          changedJavaSourcePaths
      );
      JavaCompiledArtifacts compiledArtifacts = compiledArtifactResolver.compileChangedSources(repoPath, changedJavaSourcePaths);
      JavaReflectionAugmentationResult reflection = reflectionAugmentationService.augment(
          currentNeighborhood.orderedProfiles(),
          compiledArtifacts.classpathRoots()
      );
      JavaContractDeltaReport report = computeContractDelta(baseline, currentNeighborhood, changedJavaSourcePaths, reflection.augmented());
      List<String> warnings = new ArrayList<>(warnings(currentNeighborhood, reflection, compiledArtifacts));
      String artifactId = artifactId(taskId, workerTaskId, report, warnings);
      String status = "compile-failed".equals(compiledArtifacts.status()) ? "degraded-source-only" : reflection.augmented() ? "captured" : "source-only";
      recordPostEdit(correlationId, projectKey, taskId, workerTaskId, repoPath, baseline, currentNeighborhood, reflection, report, warnings);
      return new JavaSymbolPostEditResult(
          status,
          reflection.augmented(),
          changedJavaSourcePaths,
          currentNeighborhood.orderedProfiles(),
          report,
          List.copyOf(warnings),
          artifactId,
          artifactSummary(report)
      );
    } catch (RuntimeException exception) {
      String errorMessage = errorMessage(exception);
      JavaContractDeltaReport report = contractDeltaService.parseFailure(changedJavaSourcePaths, "Java source capture failed: " + errorMessage);
      List<String> warnings = List.of(errorMessage);
      String artifactId = artifactId(taskId, workerTaskId, report, warnings);
      return new JavaSymbolPostEditResult("failed-source-read", false, changedJavaSourcePaths, List.of(), report, warnings, artifactId, artifactSummary(report));
    }
  }

  public JavaContractDeltaReport computeContractDelta(
      JavaSymbolBaseline baseline,
      JavaSymbolNeighborhood currentNeighborhood,
      List<String> changedSourcePaths,
      boolean reflectionAugmented
  ) {
    if (currentNeighborhood == null) {
      return contractDeltaService.skipped("skipped", changedSourcePaths, "Java symbol neighborhood was unavailable.");
    }
    JavaSymbolNeighborhood baselineNeighborhood = baseline == null || baseline.catalog() == null
        ? new JavaSymbolNeighborhood(List.of(), List.of(), List.of(), List.of())
        : neighborhoodBuilder.buildNeighborhood(baseline.catalog(), changedSourcePaths, "", changedSourcePaths);
    return contractDeltaService.compare(
        baselineNeighborhood.orderedProfiles(),
        currentNeighborhood.orderedProfiles(),
        changedSourcePaths,
        reflectionAugmented
    );
  }

  private JavaSymbolBaseline skippedBaseline(String correlationId, String baseRevision) {
    JavaSymbolRunContext runContext = promptRenderer.buildRunContext(null, List.of(), false, List.of());
    return new JavaSymbolBaseline(
        correlationId,
        "skipped-no-java",
        false,
        false,
        baseRevision,
        new JavaSourceSymbolCatalog(java.util.Map.of(), java.util.Map.of()),
        List.of(),
        new JavaSymbolNeighborhood(List.of(), List.of(), List.of(), List.of()),
        runContext,
        List.of()
    );
  }

  private JavaSymbolBaseline failedBaseline(String correlationId, String baseRevision, String message) {
    List<String> warnings = message == null || message.isBlank() ? List.of("Java source capture failed.") : List.of(message);
    return new JavaSymbolBaseline(
        correlationId,
        "failed-source-read",
        true,
        false,
        baseRevision,
        new JavaSourceSymbolCatalog(java.util.Map.of(), java.util.Map.of()),
        List.of(),
        new JavaSymbolNeighborhood(List.of(), List.of(), List.of(), warnings),
        promptRenderer.buildRunContext(null, warnings, false, List.of()),
        warnings
    );
  }

  private List<String> warnings(
      JavaSymbolNeighborhood neighborhood,
      JavaReflectionAugmentationResult reflection,
      JavaCompiledArtifacts compiledArtifacts
  ) {
    List<String> warnings = new ArrayList<>(mergeWarnings(neighborhood.warnings(), reflection.warnings()));
    if ("compile-failed".equals(compiledArtifacts.status()) && !compiledArtifacts.output().isBlank()) {
      warnings.add("Post-edit compile failed: " + compiledArtifacts.output());
    }
    return List.copyOf(warnings);
  }

  private List<String> recentValidationSummaries(String taskId) {
    if (taskId == null || taskId.isBlank()) {
      return List.of();
    }
    return validationReportRepository.listReportsByTask(taskId).stream()
        .limit(3)
        .map(ValidationReport::summary)
        .filter(summary -> summary != null && !summary.isBlank())
        .toList();
  }

  private String artifactId(String taskId, String workerTaskId, JavaContractDeltaReport report, List<String> warnings) {
    if (taskId == null || taskId.isBlank()) {
      return "";
    }
    return artifactService.writeArtifact(
        taskId,
        workerTaskId,
        "java-contract-delta",
        artifactSummary(report),
        artifactBody(report, warnings),
        java.util.Map.of(
            "contractDeltaStatus", report.status(),
            "reflectionAugmented", report.reflectionAugmented(),
            "changedSourcePaths", report.changedSourcePaths()
        )
    ).artifactId();
  }

  private String artifactSummary(JavaContractDeltaReport report) {
    return report == null ? "Java contract delta unavailable." : report.summary();
  }

  private String artifactBody(JavaContractDeltaReport report, List<String> warnings) {
    StringBuilder builder = new StringBuilder();
    builder.append(report.summary()).append('\n');
    for (JavaContractChange change : report.changes()) {
      builder.append("- ").append(change.kind()).append(" | ").append(change.target()).append(" | ").append(change.detail()).append('\n');
    }
    for (String warning : warnings) {
      builder.append("warning: ").append(warning).append('\n');
    }
    return builder.toString().strip();
  }

  private void recordBaseline(
      String correlationId,
      String projectKey,
      String taskId,
      String workerTaskId,
      Path repoPath,
      String baseRevision,
      JavaSourceSymbolCatalog catalog,
      JavaSymbolNeighborhood neighborhood,
      JavaReflectionAugmentationResult reflection,
      List<String> warnings
  ) {
    try {
      cacheProfiles(correlationId, "baseline", catalog.profilesByClassName().values().stream().toList());
      cacheNeighborhood(correlationId, "baseline", neighborhood);
      javaSymbolDocumentStore.storeSnapshot(
          "baseline",
          correlationId,
          projectKey,
          taskId,
          workerTaskId,
          repoPath.toString(),
          baseRevision,
          neighborhood,
          reflection,
          warnings
      );
    } catch (RuntimeException exception) {
      warnings.add("Java symbol baseline persistence degraded: " + errorMessage(exception));
    }
    queueSemanticProfiles(projectKey, taskId, workerTaskId, neighborhood.orderedProfiles(), warnings);
  }

  private void recordPostEdit(
      String correlationId,
      String projectKey,
      String taskId,
      String workerTaskId,
      Path repoPath,
      JavaSymbolBaseline baseline,
      JavaSymbolNeighborhood currentNeighborhood,
      JavaReflectionAugmentationResult reflection,
      JavaContractDeltaReport report,
      List<String> warnings
  ) {
    try {
      cacheProfiles(correlationId, "post-edit", currentNeighborhood.orderedProfiles());
      cacheNeighborhood(correlationId, "post-edit", currentNeighborhood);
      contractDeltaCache.put(correlationId, CacheDomain.JAVA, CacheType.JAVA_CONTRACT_DELTA, CacheSource.MEMORY, report, 600_000L);
      javaSymbolDocumentStore.storeSnapshot(
          "post-edit",
          correlationId,
          projectKey,
          taskId,
          workerTaskId,
          repoPath.toString(),
          baseline == null ? "" : baseline.baseRevision(),
          currentNeighborhood,
          reflection,
          warnings
      );
      javaSymbolDocumentStore.storeContractDelta(correlationId, projectKey, taskId, workerTaskId, repoPath.toString(), report);
    } catch (RuntimeException exception) {
      warnings.add("Java symbol post-edit persistence degraded: " + errorMessage(exception));
    }
    queueSemanticProfiles(projectKey, taskId, workerTaskId, currentNeighborhood.orderedProfiles(), warnings);
  }

  private void queueSemanticProfiles(
      String projectKey,
      String taskId,
      String workerTaskId,
      List<JavaClassProfile> profiles,
      List<String> warnings
  ) {
    try {
      javaSymbolSemanticIndexingService.queueProfiles(projectKey, taskId, workerTaskId, profiles);
    } catch (RuntimeException exception) {
      warnings.add("Java symbol semantic queue degraded: " + errorMessage(exception));
    }
  }

  private void cacheProfiles(String correlationId, String phase, List<JavaClassProfile> profiles) {
    for (JavaClassProfile profile : profiles) {
      profileCache.put(
          correlationId + ":" + phase + ":" + profile.qualifiedName(),
          CacheDomain.JAVA,
          CacheType.JAVA_SYMBOL_PROFILE,
          CacheSource.MEMORY,
          profile,
          600_000L
      );
    }
  }

  private void cacheNeighborhood(String correlationId, String phase, JavaSymbolNeighborhood neighborhood) {
    neighborhoodCache.put(
        correlationId + ":" + phase,
        CacheDomain.JAVA,
        CacheType.JAVA_SYMBOL_NEIGHBORHOOD,
        CacheSource.MEMORY,
        neighborhood,
        600_000L
    );
  }

  private List<String> mergeWarnings(List<String> first, List<String> second) {
    List<String> warnings = new ArrayList<>();
    if (first != null) {
      warnings.addAll(first);
    }
    if (second != null) {
      warnings.addAll(second);
    }
    return warnings.stream().filter(warning -> warning != null && !warning.isBlank()).distinct().toList();
  }

  private String errorMessage(RuntimeException exception) {
    String message = exception == null ? "" : exception.getMessage();
    if (message != null && !message.isBlank()) {
      return message;
    }
    if (exception == null) {
      return "RuntimeException";
    }
    StackTraceElement[] stackTrace = exception.getStackTrace();
    if (stackTrace == null || stackTrace.length == 0) {
      return exception.getClass().getSimpleName();
    }
    return exception.getClass().getSimpleName() + " at " + stackTrace[0];
  }
}

