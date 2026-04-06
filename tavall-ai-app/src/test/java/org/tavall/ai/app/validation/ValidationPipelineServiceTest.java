package org.tavall.ai.app.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tavall.ai.app.model.validation.ValidationEngine;
import org.tavall.ai.app.model.validation.ValidationReport;
import org.tavall.ai.app.support.IntegrationTestSupport;
import org.tavall.ai.app.support.TestWorkspacePaths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class ValidationPipelineServiceTest extends IntegrationTestSupport {

  private static final String TASK_ID = "validation-test";

  @Autowired
  private ValidationPipelineService validationPipelineService;

  @Autowired
  private JdbcClient jdbcClient;

  @BeforeEach
  void cleanup() {
    jdbcClient.sql("DELETE FROM agent_task_manager.validation_violations").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.validation_reports").update();
    jdbcClient.sql("DELETE FROM agent_task_manager.agent_tasks WHERE task_id = :taskId")
        .param("taskId", TASK_ID)
        .update();
  }

  @Test
  void shouldFailLintForExternalRepository(@TempDir Path tempDir) throws Exception {
    Path repoPath = tempDir.resolve("external-repo");
    Files.createDirectories(repoPath.resolve("src/main/java/example"));
    Files.writeString(
        repoPath.resolve("src/main/java/example/FixtureApp.java"),
        """
        package example;

        public class FixtureApp {
        }
        """,
        StandardCharsets.UTF_8
    );
    jdbcClient.sql("""
        INSERT INTO agent_task_manager.agent_tasks (
          task_id,
          project_key,
          source_repo,
          task_kind,
          title,
          status
        ) VALUES (
          :taskId,
          'validation-test-project',
          :sourceRepo,
          'general',
          'Validation test task',
          'QUEUED'
        )
        """)
        .param("taskId", TASK_ID)
        .param("sourceRepo", repoPath.toString())
        .update();

    ValidationReport report = validationPipelineService.runValidationPipeline(TASK_ID, null, repoPath);

    assertEquals("failed", report.status());
    assertTrue(report.violations().stream().anyMatch(violation -> "lint.checkstyle.unsupported-repo".equals(violation.ruleId())));
    assertTrue(report.violations().stream().anyMatch(violation -> "lint.pmd.unsupported-repo".equals(violation.ruleId())));
    assertTrue(report.violations().stream().anyMatch(violation -> "lint.error-prone.unsupported-repo".equals(violation.ruleId())));
  }

  @Test
  void shouldDetectCurrentMultiModuleProjectWithoutPackageRootRules() {
    assertTrue(AgentTaskManagerProjectLayout.isProjectRoot(TestWorkspacePaths.repoRoot()));
  }

  @Test
  void shouldExposeUnsupportedExternalRepoAsLintToolFailure(@TempDir Path tempDir) {
    Path repoPath = tempDir.resolve("external-lint-repo");
    ValidationReport report = validationPipelineService.runJavaLintValidation(TASK_ID, null, repoPath);

    assertEquals("failed", report.status());
    assertTrue(report.violations().stream().anyMatch(violation -> violation.engineSource() == ValidationEngine.CHECKSTYLE));
    assertTrue(report.violations().stream().anyMatch(violation -> violation.engineSource() == ValidationEngine.PMD));
    assertTrue(report.violations().stream().anyMatch(violation -> violation.engineSource() == ValidationEngine.ERROR_PRONE));
  }
}

