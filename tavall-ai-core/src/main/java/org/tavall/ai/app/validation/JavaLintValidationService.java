package org.tavall.ai.app.validation;

import org.tavall.ai.app.model.validation.ValidationEngine;
import org.tavall.ai.app.model.validation.ValidationSeverity;
import org.tavall.ai.app.model.validation.ValidationViolation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class JavaLintValidationService {

  private static final String CHECKSTYLE_REPORT_NAME = "checkstyle-result.xml";
  private static final String PMD_REPORT_NAME = "pmd.xml";

  private final JavaLintGradleExecutor gradleExecutor = new JavaLintGradleExecutor();
  private final JavaLintReportParser reportParser = new JavaLintReportParser();

  public List<ValidationViolation> runValidation(Path repoRoot) {
    Path normalizedRepoRoot = repoRoot.toAbsolutePath().normalize();
    if (!AgentTaskManagerProjectLayout.isGradleProjectRoot(normalizedRepoRoot)) {
      return unsupportedRepoViolations(normalizedRepoRoot);
    }

    List<ValidationViolation> violations = new ArrayList<>();
    violations.addAll(runCheckstyle(normalizedRepoRoot));
    violations.addAll(runPmd(normalizedRepoRoot));
    violations.addAll(runErrorProne(normalizedRepoRoot));
    return violations;
  }

  private List<ValidationViolation> runCheckstyle(Path repoRoot) {
    cleanupReports(repoRoot, CHECKSTYLE_REPORT_NAME);
    JavaLintGradleExecutor.EngineRunResult run = gradleExecutor.runTasks(repoRoot, List.of(
        "checkstyleMain",
        "checkstyleTest"
    ));
    List<ValidationViolation> violations = reportParser.parseCheckstyleReports(repoRoot, CHECKSTYLE_REPORT_NAME);
    if (run.exitCode() != 0 && violations.isEmpty()) {
      violations.add(executionFailure(
          ValidationEngine.CHECKSTYLE,
          "lint.checkstyle.execution-failed",
          repoRoot,
          run.output()
      ));
    }
    return violations;
  }

  private List<ValidationViolation> runPmd(Path repoRoot) {
    cleanupReports(repoRoot, PMD_REPORT_NAME);
    JavaLintGradleExecutor.EngineRunResult run = gradleExecutor.runTasks(repoRoot, List.of(
        "pmdMain",
        "pmdTest"
    ));
    List<ValidationViolation> violations = reportParser.parsePmdReports(repoRoot, PMD_REPORT_NAME);
    if (run.exitCode() != 0 && violations.isEmpty()) {
      violations.add(executionFailure(
          ValidationEngine.PMD,
          "lint.pmd.execution-failed",
          repoRoot,
          run.output()
      ));
    }
    return violations;
  }

  private List<ValidationViolation> runErrorProne(Path repoRoot) {
    JavaLintGradleExecutor.EngineRunResult run = gradleExecutor.runTasks(repoRoot, List.of(
        "compileJava",
        "compileTestJava",
        "--rerun-tasks"
    ));
    List<ValidationViolation> violations = reportParser.parseErrorProneDiagnostics(run.output());
    if (run.exitCode() != 0 && violations.isEmpty()) {
      violations.add(executionFailure(
          ValidationEngine.ERROR_PRONE,
          "lint.error-prone.execution-failed",
          repoRoot,
          run.output()
      ));
    }
    return violations;
  }

  private void cleanupReports(Path repoRoot, String reportName) {
    for (Path reportPath : reportParser.listReportFiles(repoRoot, reportName)) {
      try {
        Files.deleteIfExists(reportPath);
      } catch (IOException ignored) {
        // Best effort cleanup for generated lint reports.
      }
    }
  }

  private List<ValidationViolation> unsupportedRepoViolations(Path repoRoot) {
    String explanation = "Lint requires the AgentTaskManager multi-module repository layout and lint configuration files.";
    String remediation = "Run lint against the AgentTaskManager root repository or align the external repository with this lint baseline.";
    return List.of(
        new ValidationViolation(
            "lint.checkstyle.unsupported-repo",
            ValidationSeverity.ERROR,
            "repository",
            repoRoot.toString(),
            ValidationEngine.CHECKSTYLE,
            explanation,
            remediation
        ),
        new ValidationViolation(
            "lint.pmd.unsupported-repo",
            ValidationSeverity.ERROR,
            "repository",
            repoRoot.toString(),
            ValidationEngine.PMD,
            explanation,
            remediation
        ),
        new ValidationViolation(
            "lint.error-prone.unsupported-repo",
            ValidationSeverity.ERROR,
            "repository",
            repoRoot.toString(),
            ValidationEngine.ERROR_PRONE,
            explanation,
            remediation
        )
    );
  }

  private ValidationViolation executionFailure(
      ValidationEngine engine,
      String ruleId,
      Path repoRoot,
      String output
  ) {
    String details = output == null ? "" : output.strip();
    return new ValidationViolation(
        ruleId,
        ValidationSeverity.ERROR,
        "repository",
        repoRoot.toString(),
        engine,
        details.isBlank() ? "Lint engine execution failed." : "Lint engine execution failed: " + details,
        "Fix lint engine configuration or runtime availability before retrying."
    );
  }
}
