package org.tavall.ai.app.validation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.tavall.ai.app.model.validation.ValidationSeverity;
import org.tavall.ai.app.model.validation.ValidationViolation;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ArchUnitValidationService {

  public List<ValidationViolation> runValidation() {
    ClassFileImporter importer = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS);
    JavaClasses classes = importer.importPackages(
        AgentTaskManagerProjectLayout.applicationPackage(),
        AgentTaskManagerProjectLayout.cachePackage()
    );
    JavaClasses cycleClasses = importer.importPackages(
        AgentTaskManagerProjectLayout.cyclePackages().toArray(String[]::new)
    );
    List<ValidationViolation> violations = new ArrayList<>();

    evaluateRule(
        "arch.validation.no-web-dependency",
        ValidationSeverity.ERROR,
        noClasses()
            .that().resideInAnyPackage("..validation..")
            .should().dependOnClassesThat().resideInAnyPackage("..web.."),
        classes,
        violations
    );
    evaluateRule(
        "arch.persistence.no-web-dependency",
        ValidationSeverity.ERROR,
        noClasses()
            .that().resideInAnyPackage("..persistence..")
            .should().dependOnClassesThat().resideInAnyPackage("..web.."),
        classes,
        violations
    );
    evaluateRule(
        "arch.remote-mcp.boundary",
        ValidationSeverity.ERROR,
        noClasses()
            .that().resideOutsideOfPackages("..mcp..", "..cli..", "..config..")
            .should().dependOnClassesThat().resideInAnyPackage("io.modelcontextprotocol.."),
        classes,
        violations
    );
    evaluateRule(
        "arch.forbidden-di-frameworks",
        ValidationSeverity.ERROR,
        noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.google.inject..",
                "dagger..",
                "jakarta.inject..",
                "javax.inject.."
            ),
        classes,
        violations
    );
    evaluateRule(
        "arch.cache.boundary.clean",
        ValidationSeverity.WARNING,
        noClasses()
            .that().resideInAnyPackage("..web..")
            .should().dependOnClassesThat().resideInAnyPackage("..persistence.qdrant..", "..persistence.mongo..")
            .allowEmptyShould(true),
        classes,
        violations
    );
    evaluateRule(
        "arch.project.slices.cycle-free",
        ValidationSeverity.ERROR,
        slices().matching(AgentTaskManagerProjectLayout.slicePattern()).should().beFreeOfCycles(),
        cycleClasses,
        violations
    );

    return violations;
  }

  private void evaluateRule(
      String ruleId,
      ValidationSeverity severity,
      ArchRule rule,
      JavaClasses classes,
      List<ValidationViolation> violations
  ) {
    EvaluationResult result = rule.evaluate(classes);
    for (String detail : result.getFailureReport().getDetails()) {
      violations.add(new ValidationViolation(
          ruleId,
          severity,
          "package",
          AgentTaskManagerProjectLayout.applicationPackage(),
          org.tavall.ai.app.model.validation.ValidationEngine.ARCHUNIT,
          detail,
          "Adjust the dependency boundary to match the documented package map."
      ));
    }
  }
}
