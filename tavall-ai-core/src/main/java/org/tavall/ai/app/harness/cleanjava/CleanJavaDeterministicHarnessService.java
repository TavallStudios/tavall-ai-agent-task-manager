package org.tavall.ai.app.harness.cleanjava;

import org.tavall.ai.app.model.validation.ValidationReport;
import org.tavall.ai.app.model.validation.ValidationViolation;
import org.tavall.ai.app.validation.ValidationPipelineService;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CleanJavaDeterministicHarnessService {

  private static final String CYCLE_RULE_ID = "arch.project.slices.cycle-free";

  private final CleanJavaTaskContextService cleanJavaTaskContextService;
  private final ValidationPipelineService validationPipelineService;

  public CleanJavaDeterministicHarnessService(
      CleanJavaTaskContextService cleanJavaTaskContextService,
      ValidationPipelineService validationPipelineService
  ) {
    this.cleanJavaTaskContextService = cleanJavaTaskContextService;
    this.validationPipelineService = validationPipelineService;
  }

  public CleanJavaHarnessRunResult run(
      String taskId,
      String workerTaskId,
      String projectKey,
      Path repoPath,
      String queryText
  ) {
    Path normalizedRepoPath = repoPath.toAbsolutePath().normalize();
    CleanJavaTaskContext taskContext = cleanJavaTaskContextService.buildContext(
        taskId,
        workerTaskId,
        projectKey,
        normalizedRepoPath,
        queryText
    );
    ValidationReport lintReport = validationPipelineService.runJavaLintValidation(taskId, workerTaskId, normalizedRepoPath);
    ValidationReport spoonReport = validationPipelineService.runSpoonValidation(taskId, workerTaskId, normalizedRepoPath);
    ValidationReport archUnitReport = validationPipelineService.runArchUnitValidation(taskId, workerTaskId, normalizedRepoPath);
    ValidationReport storedReport = validationPipelineService.storeValidationReport(
        taskId,
        workerTaskId,
        validationPipelineService.mergeReports(taskId, workerTaskId, List.of(lintReport, spoonReport, archUnitReport))
    );
    return new CleanJavaHarnessRunResult(
        taskContext,
        stage("lint", lintReport.violations(), lintReport.summary(), lintReport.status()),
        stage("source-shape", spoonReport.violations(), spoonReport.summary(), spoonReport.status()),
        stage("architecture", nonCycleViolations(archUnitReport), archUnitReport.summary(), archUnitReport.status()),
        stage("cycle-check", cycleViolations(archUnitReport), cycleSummary(archUnitReport), cycleStatus(archUnitReport)),
        storedReport,
        "passed".equalsIgnoreCase(storedReport.status())
    );
  }

  private CleanJavaValidationStageResult stage(
      String stageName,
      List<ValidationViolation> violations,
      String summary,
      String status
  ) {
    return new CleanJavaValidationStageResult(stageName, status, summary, violations);
  }

  private List<ValidationViolation> nonCycleViolations(ValidationReport report) {
    return report.violations().stream()
        .filter(violation -> !CYCLE_RULE_ID.equals(violation.ruleId()))
        .toList();
  }

  private List<ValidationViolation> cycleViolations(ValidationReport report) {
    return report.violations().stream()
        .filter(violation -> CYCLE_RULE_ID.equals(violation.ruleId()))
        .toList();
  }

  private String cycleSummary(ValidationReport report) {
    List<ValidationViolation> cycles = cycleViolations(report);
    if ("skipped".equalsIgnoreCase(report.status())) {
      return "Cycle check skipped because ArchUnit is not active for this repository.";
    }
    if (cycles.isEmpty()) {
      return "Cycle check passed with no dependency cycles.";
    }
    return "Cycle check found " + cycles.size() + " violation(s).";
  }

  private String cycleStatus(ValidationReport report) {
    List<ValidationViolation> cycles = cycleViolations(report);
    if ("skipped".equalsIgnoreCase(report.status())) {
      return "skipped";
    }
    return cycles.isEmpty() ? "passed" : "failed";
  }
}

