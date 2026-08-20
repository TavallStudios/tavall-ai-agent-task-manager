package org.tavall.ai.app.validation;

import org.tavall.ai.app.model.validation.ValidationEngine;
import org.tavall.ai.app.model.validation.ValidationSeverity;
import org.tavall.ai.app.model.validation.ValidationViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

public class SpoonCodeRuleSet {

  private static final List<String> BANNED_EXACT_NAMES = List.of(
      "handleMove",
      "joinPlayer",
      "finishStop",
      "data",
      "map"
  );

  private static final List<String> BANNED_SUFFIXES = List.of("manager", "util", "helper");

  public List<ValidationViolation> collectViolations(CtModel model) {
    List<ValidationViolation> violations = new ArrayList<>();
    for (CtType<?> type : model.getAllTypes()) {
      if (type.isImplicit() || type.getPosition() == null || !type.getPosition().isValidPosition()) {
        continue;
      }

      validateLineCount(type, violations);
      validateSingleConcern(type, violations);
      validateNaming(type, violations);
      validateDependencyAccessRules(type, violations);
      validateDirectCallRules(type, violations);
      validatePublicMethodDocs(type, violations);
      validateInnerTypes(type, violations);
      validateComments(type, violations);
      validateNullBranchStyle(type, violations);
      validateInlineCallStyle(type, violations);
    }
    return violations;
  }

  private void validateLineCount(CtType<?> type, List<ValidationViolation> violations) {
    int lineCount = type.getPosition().getEndLine() - type.getPosition().getLine() + 1;
    if (lineCount > 300) {
      violations.add(violation(
          "spoon.class.line-count",
          ValidationSeverity.ERROR,
          "class",
          type.getQualifiedName(),
          "Top-level type exceeds the 300 line limit with " + lineCount + " lines.",
          "Split the type by concern before adding more logic."
      ));
    }
  }

  private void validateSingleConcern(CtType<?> type, List<ValidationViolation> violations) {
    long methodCount = type.getMethods().stream().filter(method -> !method.isImplicit()).count();
    long fieldCount = type.getFields().stream().filter(field -> !field.isImplicit()).count();
    if (methodCount > 12 && fieldCount > 8) {
      violations.add(violation(
          "spoon.class.single-concern",
          ValidationSeverity.WARNING,
          "class",
          type.getQualifiedName(),
          "Type trips the single-concern heuristic with " + methodCount + " methods and " + fieldCount + " fields.",
          "Split workflow, persistence, and view concerns into separate classes."
      ));
    }
  }

  private void validateNaming(CtType<?> type, List<ValidationViolation> violations) {
    String simpleName = type.getSimpleName();
    validateName("class", type.getQualifiedName(), simpleName, violations);
    for (CtMethod<?> method : type.getMethods()) {
      validateName("method", type.getQualifiedName() + "#" + method.getSimpleName(), method.getSimpleName(), violations);
    }
    type.getFields().forEach(field -> validateName(
        "field",
        type.getQualifiedName() + "#" + field.getSimpleName(),
        field.getSimpleName(),
        violations
    ));
  }

  private void validateDependencyAccessRules(CtType<?> type, List<ValidationViolation> violations) {
    if (!type.isInterface() || !type.getSimpleName().endsWith("DependencyAccess")) {
      return;
    }

    for (CtMethod<?> method : type.getMethods()) {
      if (!method.isDefaultMethod()) {
        continue;
      }
      String methodName = method.getSimpleName();
      if (methodName.matches("^get.+(Service|Registry)$")) {
        violations.add(violation(
            "spoon.dependency-access.raw-getter",
            ValidationSeverity.ERROR,
            "method",
            type.getQualifiedName() + "#" + methodName,
            "Dependency access interfaces must expose actions instead of raw service getters.",
            "Replace raw getters with explicit action methods."
        ));
      }
      int statementCount = method.getBody() == null ? 0 : method.getBody().getStatements().size();
      if (statementCount > 1) {
        violations.add(violation(
            "spoon.dependency-access.forwarding-only",
            ValidationSeverity.WARNING,
            "method",
            type.getQualifiedName() + "#" + methodName,
            "Default dependency-access methods should stay as simple forwarding methods.",
            "Keep the default method body to a single delegated call."
        ));
      }
    }
  }

  private void validateDirectCallRules(CtType<?> type, List<ValidationViolation> violations) {
    boolean implementsDependencyAccess = type.getSuperInterfaces().stream()
        .anyMatch(superInterface -> superInterface.getSimpleName().endsWith("DependencyAccess"));
    if (!implementsDependencyAccess) {
      return;
    }

    for (CtMethod<?> method : type.getMethods()) {
      String source = method.toString();
      if (source.contains(".get") && (source.contains("Service()") || source.contains("Registry()"))) {
        violations.add(violation(
            "spoon.concrete.direct-call",
            ValidationSeverity.ERROR,
            "method",
            type.getQualifiedName() + "#" + method.getSimpleName(),
            "Concrete classes implementing dependency access should use inherited action methods directly.",
            "Remove getter chaining and call the composed action method instead."
        ));
      }
    }
  }

  private void validatePublicMethodDocs(CtType<?> type, List<ValidationViolation> violations) {
    for (CtMethod<?> method : type.getMethods()) {
      if (!method.isPublic()) {
        continue;
      }
      boolean inCorePackage = type.getQualifiedName().contains(".orchestration.")
          || type.getQualifiedName().contains(".validation.")
          || type.getQualifiedName().contains(".mcp.");
      if (!inCorePackage) {
        continue;
      }
      boolean hasJavadoc = method.getComments().stream().anyMatch(comment -> comment.getCommentType() == CtComment.CommentType.JAVADOC);
      if (!hasJavadoc) {
        violations.add(violation(
            "spoon.javadoc.required",
            ValidationSeverity.WARNING,
            "method",
            type.getQualifiedName() + "#" + method.getSimpleName(),
            "Public core methods should use the required JavaDoc format.",
            "Add a JavaDoc block with a summary and parameter tags."
        ));
      }
    }
  }

  private void validateInnerTypes(CtType<?> type, List<ValidationViolation> violations) {
    type.getNestedTypes().forEach(nestedType -> {
      boolean hasWorkflowMethod = nestedType.getMethods().stream()
          .anyMatch(method -> !method.getSimpleName().startsWith("get")
              && !method.getSimpleName().startsWith("is")
              && !method.getSimpleName().equals(nestedType.getSimpleName()));
      if (hasWorkflowMethod) {
        violations.add(violation(
            "spoon.inner-class.metadata-only",
            ValidationSeverity.ERROR,
            "class",
            nestedType.getQualifiedName(),
            "Inner types are restricted to metadata grouping and getters.",
            "Promote workflow inner classes to top-level types."
        ));
      }
    });
  }

  private void validateComments(CtType<?> type, List<ValidationViolation> violations) {
    for (CtComment comment : type.getComments()) {
      String content = comment.getContent().strip();
      String normalized = content.toLowerCase(Locale.ROOT);
      if (normalized.contains("edge case") && !content.startsWith(" Edge Case:") && !content.startsWith("Edge Case:")) {
        violations.add(violation(
            "spoon.edge-case.comment-format",
            ValidationSeverity.WARNING,
            "comment",
            type.getQualifiedName(),
            "Edge-case comments must use the exact `// Edge Case: <reason>` format.",
            "Rewrite the comment to match the required inline format."
        ));
      }
    }
  }

  private void validateNullBranchStyle(CtType<?> type, List<ValidationViolation> violations) {
    for (CtIf ifStatement : type.getElements(element -> element instanceof CtIf).stream().map(CtIf.class::cast).toList()) {
      String condition = ifStatement.getCondition().toString();
      if (condition.contains("== null") && ifStatement.getElseStatement() != null) {
        violations.add(violation(
            "spoon.null-branch.positive-first",
            ValidationSeverity.WARNING,
            "method",
            type.getQualifiedName(),
            "Prefer the positive non-null branch first instead of a null-first if/else.",
            "Flip the condition and keep the happy path in the main branch."
        ));
      }
    }
  }

  private void validateInlineCallStyle(CtType<?> type, List<ValidationViolation> violations) {
    for (CtMethod<?> method : type.getMethods()) {
      if (method.getBody() == null || method.getBody().getStatements().size() != 2) {
        continue;
      }
      CtElement first = method.getBody().getStatements().getFirst();
      CtElement second = method.getBody().getStatements().get(1);
      if (first instanceof CtLocalVariable<?> && second instanceof CtReturn<?>) {
        violations.add(violation(
            "spoon.inline-call.style",
            ValidationSeverity.INFO,
            "method",
            type.getQualifiedName() + "#" + method.getSimpleName(),
            "Simple delegated calls should stay inline when readability is still clear.",
            "Return the delegated call directly if the temporary variable adds no value."
        ));
      }
    }
  }

  private void validateName(String targetType, String targetName, String name, List<ValidationViolation> violations) {
    if (BANNED_EXACT_NAMES.contains(name)) {
      violations.add(violation(
          "spoon.naming.explicit",
          ValidationSeverity.ERROR,
          targetType,
          targetName,
          "Name `" + name + "` is banned by the explicit naming rule.",
          "Rename the element to describe the actual behavior."
      ));
      return;
    }
    String normalized = name.toLowerCase(Locale.ROOT);
    if (BANNED_SUFFIXES.stream().anyMatch(normalized::endsWith)) {
      violations.add(violation(
          "spoon.naming.vague-suffix",
          ValidationSeverity.WARNING,
          targetType,
          targetName,
          "Name `" + name + "` uses a vague suffix that obscures the real concern.",
          "Choose a name that states the concrete behavior or domain."
      ));
    }
  }

  private ValidationViolation violation(
      String ruleId,
      ValidationSeverity severity,
      String targetType,
      String targetName,
      String explanation,
      String remediation
  ) {
    return new ValidationViolation(ruleId, severity, targetType, targetName, ValidationEngine.SPOON, explanation, remediation);
  }
}
