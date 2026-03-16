package com.agenttaskmanager.app.validation;

import cache.CacheDomain;
import cache.CacheSource;
import cache.CacheType;
import cache.ValidationSummaryCache;
import com.agenttaskmanager.app.persistence.postgres.ValidationReportRepository;
import com.agenttaskmanager.app.model.validation.ValidationReport;
import com.agenttaskmanager.app.model.validation.ValidationSeverity;
import com.agenttaskmanager.app.model.validation.ValidationViolation;
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
import org.springframework.stereotype.Service;

@Service
public class ValidationPipelineService {

  private final ArchUnitValidationService archUnitValidationService;
  private final SpoonValidationService spoonValidationService;
  private final ValidationReportRepository validationReportRepository;
  private final ValidationSummaryCache validationSummaryCache;

  public ValidationPipelineService(
      ArchUnitValidationService archUnitValidationService,
      SpoonValidationService spoonValidationService,
      ValidationReportRepository validationReportRepository,
      ValidationSummaryCache validationSummaryCache
  ) {
    this.archUnitValidationService = archUnitValidationService;
    this.spoonValidationService = spoonValidationService;
    this.validationReportRepository = validationReportRepository;
    this.validationSummaryCache = validationSummaryCache;
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
    return diffBody != null && !diffBody.isBlank() && !diffBody.contains("/target/");
  }

  public Map<String, Object> runIntegrationTests(Path repoRoot) {
    try {
      Process process = new ProcessBuilder("mvn", "-q", "-DskipTests=false", "-DskipITs=false", "verify")
          .directory(repoRoot.toFile())
          .redirectErrorStream(true)
          .start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exitCode = process.waitFor();
      return Map.of("exitCode", exitCode, "output", output);
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return Map.of("exitCode", -1, "output", exception.getMessage());
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
}
