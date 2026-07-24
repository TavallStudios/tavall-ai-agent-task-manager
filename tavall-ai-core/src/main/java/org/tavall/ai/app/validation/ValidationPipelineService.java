package org.tavall.ai.app.validation;

import cache.CacheDomain;
import cache.CacheSource;
import cache.CacheType;
import cache.ValidationSummaryCache;
import org.tavall.ai.app.persistence.postgres.ValidationReportRepository;
import org.tavall.ai.app.model.validation.ValidationReport;
import org.tavall.ai.app.model.validation.ValidationSeverity;
import org.tavall.ai.app.model.validation.ValidationViolation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class ValidationPipelineService {

  private static final int DEFAULT_INTEGRATION_TIMEOUT_SECONDS = 120;
  private static final int MIN_INTEGRATION_TIMEOUT_SECONDS = 10;
  private static final int MAX_INTEGRATION_TIMEOUT_SECONDS = 1800;
  private static final int INTEGRATION_TIMEOUT_EXIT_CODE = 124;
  private static final int MAX_INTEGRATION_OUTPUT_CHARS = 200_000;

  private final ArchUnitValidationService archUnitValidationService;
  private final JavaLintValidationService javaLintValidationService;
  private final SpoonValidationService spoonValidationService;
  private final ValidationReportRepository validationReportRepository;
  private final ValidationSummaryCache validationSummaryCache;

  public ValidationPipelineService(
      ArchUnitValidationService archUnitValidationService,
      JavaLintValidationService javaLintValidationService,
      SpoonValidationService spoonValidationService,
      ValidationReportRepository validationReportRepository,
      ValidationSummaryCache validationSummaryCache
  ) {
    this.archUnitValidationService = archUnitValidationService;
    this.javaLintValidationService = javaLintValidationService;
    this.spoonValidationService = spoonValidationService;
    this.validationReportRepository = validationReportRepository;
    this.validationSummaryCache = validationSummaryCache;
  }

  public ValidationReport runJavaLintValidation(String taskId, String workerTaskId, Path repoRoot) {
    Path normalizedRepoRoot = repoRoot.toAbsolutePath().normalize();
    return buildReport(taskId, workerTaskId, javaLintValidationService.runValidation(normalizedRepoRoot));
  }

  public ValidationReport runArchUnitValidation(String taskId, String workerTaskId) {
    return buildReport(taskId, workerTaskId, archUnitValidationService.runValidation());
  }

  public ValidationReport runArchUnitValidation(String taskId, String workerTaskId, Path repoRoot) {
    Path normalizedRepoRoot = repoRoot.toAbsolutePath().normalize();
    if (!supportsArchUnitValidation(normalizedRepoRoot)) {
      return skippedReport(taskId, workerTaskId, "ArchUnit skipped because the repository is outside the AgentTaskManager layout.");
    }
    return runArchUnitValidation(taskId, workerTaskId);
  }

  public ValidationReport runSpoonValidation(String taskId, String workerTaskId, Path repoRoot) {
    return buildReport(taskId, workerTaskId, spoonValidationService.runValidation(repoRoot));
  }

  public ValidationReport runValidationPipeline(String taskId, String workerTaskId, Path repoRoot) {
    List<ValidationViolation> violations = new ArrayList<>();
    Path normalizedRepoRoot = repoRoot.toAbsolutePath().normalize();
    violations.addAll(javaLintValidationService.runValidation(normalizedRepoRoot));
    if (supportsArchUnitValidation(normalizedRepoRoot)) {
      violations.addAll(archUnitValidationService.runValidation());
    }
    violations.addAll(spoonValidationService.runValidation(normalizedRepoRoot));
    return storeValidationReport(taskId, workerTaskId, buildReport(taskId, workerTaskId, violations));
  }

  public ValidationReport mergeReports(String taskId, String workerTaskId, List<ValidationReport> reports) {
    List<ValidationViolation> violations = reports.stream()
        .flatMap(report -> report.violations().stream())
        .toList();
    return buildReport(taskId, workerTaskId, violations);
  }

  public boolean validatePatchScope(String diffBody) {
    return diffBody != null && !diffBody.isBlank() && !diffBody.contains("/build/");
  }

  public Map<String, Object> runIntegrationTests(Path repoRoot) {
    return runIntegrationTests(repoRoot, null);
  }

  public Map<String, Object> runIntegrationTests(Path repoRoot, Integer timeoutSeconds) {
    Path normalizedRepoRoot = repoRoot.toAbsolutePath().normalize();
    int effectiveTimeoutSeconds = normalizeIntegrationTimeout(timeoutSeconds);
    Path outputPath = null;
    try {
      outputPath = Files.createTempFile("atm-integration-tests-", ".log");
      Process process = new ProcessBuilder(
          gradleWrapper(normalizedRepoRoot),
          "--no-daemon",
          "--max-workers=1",
          "check"
      )
          .directory(normalizedRepoRoot.toFile())
          .redirectErrorStream(true)
          .redirectOutput(outputPath.toFile())
          .start();

      boolean finished = process.waitFor(effectiveTimeoutSeconds, TimeUnit.SECONDS);
      if (!finished) {
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
          process.destroyForcibly();
          process.waitFor(5, TimeUnit.SECONDS);
        }
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("exitCode", finished ? process.exitValue() : INTEGRATION_TIMEOUT_EXIT_CODE);
      result.put("output", readProcessOutput(outputPath));
      result.put("timeoutSeconds", effectiveTimeoutSeconds);
      result.put("timedOut", !finished);
      return result;
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("exitCode", -1);
      result.put("output", exception.getMessage());
      result.put("timeoutSeconds", effectiveTimeoutSeconds);
      result.put("timedOut", false);
      return result;
    } finally {
      if (outputPath != null) {
        try {
          Files.deleteIfExists(outputPath);
        } catch (IOException ignored) {
          // Best effort cleanup for temporary integration harness logs.
        }
      }
    }
  }

  public ValidationReport storeValidationReport(String taskId, String workerTaskId, ValidationReport report) {
    ValidationReport storedReport = validationReportRepository.storeReport(taskId, workerTaskId, null, report);
    cacheValidationSummary(storedReport);
    return storedReport;
  }

  public void cacheValidationSummary(ValidationReport report) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("reportId", report.reportId());
    payload.put("taskId", report.taskId());
    payload.put("workerTaskId", report.workerTaskId());
    payload.put("status", report.status());
    payload.put("complianceScore", report.complianceScore());
    payload.put("summary", report.summary());
    validationSummaryCache.put(
        cacheKey(report.taskId(), report.workerTaskId()),
        CacheDomain.VALIDATION,
        CacheType.VALIDATION_SUMMARY,
        CacheSource.POSTGRES,
        payload,
        600_000L
    );
  }

  public Map<String, Object> getCachedValidationSummary(String taskId, String workerTaskId) {
    return validationSummaryCache.getIfPresent(
        cacheKey(taskId, workerTaskId),
        CacheDomain.VALIDATION,
        CacheType.VALIDATION_SUMMARY,
        CacheSource.POSTGRES
    );
  }

  private ValidationReport buildReport(String taskId, String workerTaskId, List<ValidationViolation> violations) {
    long errorCount = violations.stream().filter(violation -> violation.severity() == ValidationSeverity.ERROR).count();
    long warningCount = violations.stream().filter(violation -> violation.severity() == ValidationSeverity.WARNING).count();
    double complianceScore = Math.max(0.0D, 100.0D - (errorCount * 15.0D) - (warningCount * 5.0D));
    String status = errorCount > 0 ? "failed" : "passed";
    String summary = violations.isEmpty()
        ? "Validation passed with no violations."
        : "Validation found " + violations.size() + " violations.";
    return new ValidationReport(
        "vr_" + UUID.randomUUID(),
        taskId,
        workerTaskId,
        status,
        complianceScore,
        summary,
        violations,
        OffsetDateTime.now()
    );
  }

  private ValidationReport skippedReport(String taskId, String workerTaskId, String summary) {
    return new ValidationReport(
        "vr_" + UUID.randomUUID(),
        taskId,
        workerTaskId,
        "skipped",
        100.0D,
        summary,
        List.of(),
        OffsetDateTime.now()
    );
  }

  private String cacheKey(String taskId, String workerTaskId) {
    return taskId + ":" + (workerTaskId == null ? "" : workerTaskId);
  }

  private boolean supportsArchUnitValidation(Path repoRoot) {
    return AgentTaskManagerProjectLayout.isProjectRoot(repoRoot);
  }

  private int normalizeIntegrationTimeout(Integer timeoutSeconds) {
    int baseTimeout = timeoutSeconds == null ? defaultIntegrationTimeoutSeconds() : timeoutSeconds;
    return Math.max(MIN_INTEGRATION_TIMEOUT_SECONDS, Math.min(baseTimeout, MAX_INTEGRATION_TIMEOUT_SECONDS));
  }

  private int defaultIntegrationTimeoutSeconds() {
    String configured = System.getenv("AGENT_TASK_MANAGER_INTEGRATION_TIMEOUT_SECONDS");
    if (configured == null || configured.isBlank()) {
      return DEFAULT_INTEGRATION_TIMEOUT_SECONDS;
    }
    try {
      return Integer.parseInt(configured.strip());
    } catch (NumberFormatException ignored) {
      return DEFAULT_INTEGRATION_TIMEOUT_SECONDS;
    }
  }

  private String readProcessOutput(Path outputPath) throws IOException {
    String output = Files.readString(outputPath, StandardCharsets.UTF_8);
    if (output.length() <= MAX_INTEGRATION_OUTPUT_CHARS) {
      return output;
    }
    return output.substring(0, MAX_INTEGRATION_OUTPUT_CHARS) + "\n... integration output truncated ...";
  }

  private String gradleWrapper(Path repoRoot) {
    String wrapperName = System.getProperty("os.name", "").toLowerCase().contains("win")
        ? "gradlew.bat"
        : "gradlew";
    return repoRoot.resolve(wrapperName).toString();
  }
}
