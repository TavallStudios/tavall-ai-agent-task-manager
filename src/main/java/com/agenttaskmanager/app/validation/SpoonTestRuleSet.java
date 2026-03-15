package com.agenttaskmanager.app.validation;

import com.agenttaskmanager.app.model.validation.ValidationEngine;
import com.agenttaskmanager.app.model.validation.ValidationSeverity;
import com.agenttaskmanager.app.model.validation.ValidationViolation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SpoonTestRuleSet {

  public List<ValidationViolation> collectViolations(Path repoRoot) {
    List<ValidationViolation> violations = new ArrayList<>();
    Path testRoot = repoRoot.resolve("src/test/java");
    if (!Files.isDirectory(testRoot)) {
      return violations;
    }

    try (var files = Files.walk(testRoot)) {
      files.filter(path -> path.toString().endsWith(".java"))
          .forEach(path -> inspectTestFile(testRoot, path, violations));
    } catch (IOException exception) {
        violations.add(new ValidationViolation(
            "spoon.tests.read-error",
            ValidationSeverity.WARNING,
            "test",
            testRoot.toString(),
            ValidationEngine.SPOON,
            "Failed to inspect test sources: " + exception.getMessage(),
            "Check filesystem permissions before rerunning validation."
        ));
    }
    return violations;
  }

  private void inspectTestFile(Path testRoot, Path path, List<ValidationViolation> violations) {
    try {
      String body = Files.readString(path, StandardCharsets.UTF_8);
      if (body.contains("org.mockito") || body.contains("@Mock") || body.contains("mock(")) {
        violations.add(new ValidationViolation(
            "spoon.tests.mocked-patterns",
            ValidationSeverity.ERROR,
            "test",
            testRoot.relativize(path).toString(),
            ValidationEngine.SPOON,
            "Fake mocked unit-test patterns are forbidden in this project.",
            "Replace the mocked test with an integration test that uses the real path."
        ));
      }
    } catch (IOException exception) {
      violations.add(new ValidationViolation(
          "spoon.tests.read-error",
          ValidationSeverity.WARNING,
          "test",
          path.toString(),
          ValidationEngine.SPOON,
          "Failed to read test source: " + exception.getMessage(),
          "Check filesystem permissions before rerunning validation."
      ));
    }
  }
}
