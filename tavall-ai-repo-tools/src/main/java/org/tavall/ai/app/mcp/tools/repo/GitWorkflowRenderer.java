package org.tavall.ai.app.mcp.tools.repo;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class GitWorkflowRenderer {

  private static final Set<String> CHANGE_TYPES = Set.of("Added", "Changed", "Fix", "Refactor", "Removed");
  private static final Set<String> SUPPORT_FILES = Set.of(
      "README.md",
      "TOOLS.md",
      "ARCHITECTURE.md",
      "HARNESS.md",
      "GIT_WORKFLOW.md",
      "build.gradle.kts",
      "settings.gradle.kts"
  );

  GitWorkflowPlan renderPlan(GitWorkflowRequest request, List<String> candidateFiles) {
    String changeType = normalizeChangeType(request.changeType());
    boolean finalChange = Boolean.TRUE.equals(request.finalChange());
    validateChangeType(changeType, finalChange);

    String branchName = branchName(request);
    String summary = required("summary", request.summary());
    String subject = changeType + ": " + summary.strip();
    String body = renderBody(request, branchName, changeType, finalChange);
    boolean mixedConcernDetected = mixedConcernDetected(candidateFiles);
    return new GitWorkflowPlan(
        branchName,
        subject,
        body,
        candidateFiles,
        groupingRecommendation(candidateFiles, mixedConcernDetected, Boolean.TRUE.equals(request.allowMixedDomain())),
        mixedConcernDetected
    );
  }

  private void validateChangeType(String changeType, boolean finalChange) {
    if (!CHANGE_TYPES.contains(changeType)) {
      throw new IllegalArgumentException("changeType must be one of Added, Changed, Fix, Refactor, or Removed.");
    }
    if (Set.of("Fix", "Refactor").contains(changeType) && !finalChange) {
      throw new IllegalArgumentException(changeType + " commits require finalChange=true.");
    }
  }

  private String branchName(GitWorkflowRequest request) {
    return String.join(
        "-",
        normalizeBranchSegment(valueOrOverride(request.domain(), request.domainOverride())),
        normalizeBranchSegment(valueOrOverride(request.system(), request.systemOverride())),
        normalizeBranchSegment(valueOrOverride(request.user(), request.userOverride())),
        normalizeVersion(valueOrOverride(request.version(), request.versionOverride()))
    );
  }

  private String renderBody(GitWorkflowRequest request, String branchName, String changeType, boolean finalChange) {
    String domain = valueOrOverride(request.domain(), request.domainOverride()).strip();
    String system = valueOrOverride(request.system(), request.systemOverride()).strip();
    return """
        What Changed:
        %s

        Why:
        Keeps the %s / %s concern isolated on %s using the first-party git workflow.

        Verification:
        %s

        Branch:
        %s

        Concern:
        %s / %s

        Final Change: %s
        Change Type: %s
        """.formatted(
        required("details", request.details()),
        domain,
        system,
        branchName,
        required("verification", request.verification()),
        branchName,
        domain,
        system,
        finalChange ? "yes" : "no",
        changeType
    ).strip();
  }

  private boolean mixedConcernDetected(List<String> candidateFiles) {
    Set<String> concernRoots = new LinkedHashSet<>();
    for (String file : candidateFiles) {
      String root = concernRoot(file);
      if (!root.isBlank()) {
        concernRoots.add(root);
      }
    }
    return concernRoots.size() > 1;
  }

  private String groupingRecommendation(List<String> candidateFiles, boolean mixedConcernDetected, boolean allowMixedDomain) {
    if (candidateFiles.isEmpty()) {
      return "No changed files were detected yet. Gather repo context, then commit one concern at a time.";
    }
    if (!mixedConcernDetected) {
      return "The pending file set stays within one concern. Commit this slice before moving to another domain.";
    }
    if (allowMixedDomain) {
      return "Mixed concern files were explicitly allowed. Keep the verbose body specific about the grouped scope.";
    }
    return "Changed files span multiple concerns. Provide filePaths or split the work before committing.";
  }

  private String concernRoot(String file) {
    Path path = Path.of(file).normalize();
    if (path.getNameCount() == 0) {
      return "";
    }
    if (path.getNameCount() == 1 && SUPPORT_FILES.contains(path.getFileName().toString())) {
      return "";
    }
    return path.getName(0).toString().toLowerCase(Locale.ROOT);
  }

  private String valueOrOverride(String value, String overrideValue) {
    return overrideValue != null && !overrideValue.isBlank() ? overrideValue : required("branch metadata", value);
  }

  private String normalizeBranchSegment(String value) {
    String normalized = value.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-+|-+$)", "")
        .replaceAll("-{2,}", "-")
        .strip();
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("Branch metadata cannot normalize to an empty value.");
    }
    return normalized;
  }

  private String normalizeVersion(String value) {
    String normalized = normalizeBranchSegment(value);
    if (!normalized.matches("v\\d+")) {
      if (normalized.matches("\\d+")) {
        return "v" + normalized;
      }
      throw new IllegalArgumentException("version must normalize to vN.");
    }
    return normalized;
  }

  private String normalizeChangeType(String changeType) {
    String normalized = required("changeType", changeType).strip().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "added" -> "Added";
      case "changed" -> "Changed";
      case "fix" -> "Fix";
      case "refactor" -> "Refactor";
      case "removed" -> "Removed";
      default -> changeType == null ? "" : changeType.strip();
    };
  }

  private String required(String label, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required.");
    }
    return value;
  }

  record GitWorkflowPlan(
      String branchName,
      String subject,
      String body,
      List<String> candidateFiles,
      String groupingRecommendation,
      boolean mixedConcernDetected
  ) {
  }
}

