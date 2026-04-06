package org.tavall.ai.app.validation;

import org.tavall.ai.app.model.validation.ValidationEngine;
import org.tavall.ai.app.model.validation.ValidationSeverity;
import org.tavall.ai.app.model.validation.ValidationViolation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class JavaLintReportParser {

  private static final Pattern ERROR_PRONE_WITH_COLUMN = Pattern.compile(
      "^(.+\\.java):\\[(\\d+),(\\d+)]\\s+error:\\s+\\[([^\\]]+)]\\s+(.+)$",
      Pattern.MULTILINE
  );
  private static final Pattern ERROR_PRONE_LINE_ONLY = Pattern.compile(
      "^(.+\\.java):(\\d+):\\s+error:\\s+\\[([^\\]]+)]\\s+(.+)$",
      Pattern.MULTILINE
  );

  List<Path> listReportFiles(Path repoRoot, String reportName) {
    try (Stream<Path> stream = Files.find(
        repoRoot,
        8,
        (path, attrs) -> attrs.isRegularFile() && path.getFileName().toString().equals(reportName)
    )) {
      return stream.sorted(Comparator.comparing(Path::toString)).toList();
    } catch (IOException exception) {
      return List.of();
    }
  }

  List<ValidationViolation> parseCheckstyleReports(Path repoRoot, String reportName) {
    List<ValidationViolation> violations = new ArrayList<>();
    for (Path report : listReportFiles(repoRoot, reportName)) {
      try {
        Document document = readXml(report);
        NodeList files = document.getElementsByTagName("file");
        for (int fileIndex = 0; fileIndex < files.getLength(); fileIndex++) {
          Element fileElement = (Element) files.item(fileIndex);
          String fileName = fileElement.getAttribute("name");
          NodeList errors = fileElement.getElementsByTagName("error");
          for (int errorIndex = 0; errorIndex < errors.getLength(); errorIndex++) {
            Element error = (Element) errors.item(errorIndex);
            ValidationSeverity severity = mapCheckstyleSeverity(error.getAttribute("severity"));
            if (severity == null) {
              continue;
            }
            String source = error.getAttribute("source");
            String ruleId = "lint.checkstyle." + sanitizeRuleId(shortRuleName(source, "violation"));
            String targetName = formatTarget(fileName, error.getAttribute("line"), error.getAttribute("column"));
            violations.add(new ValidationViolation(
                ruleId,
                severity,
                "file",
                targetName,
                ValidationEngine.CHECKSTYLE,
                trim(error.getAttribute("message")),
                "Update source code to satisfy Checkstyle rules."
            ));
          }
        }
      } catch (Exception exception) {
        violations.add(parseFailure(
            ValidationEngine.CHECKSTYLE,
            "lint.checkstyle.report-parse-failed",
            report,
            exception.getMessage()
        ));
      }
    }
    return violations;
  }

  List<ValidationViolation> parsePmdReports(Path repoRoot, String reportName) {
    List<ValidationViolation> violations = new ArrayList<>();
    for (Path report : listReportFiles(repoRoot, reportName)) {
      try {
        Document document = readXml(report);
        NodeList files = document.getElementsByTagName("file");
        for (int fileIndex = 0; fileIndex < files.getLength(); fileIndex++) {
          Element fileElement = (Element) files.item(fileIndex);
          String fileName = fileElement.getAttribute("name");
          NodeList fileViolations = fileElement.getElementsByTagName("violation");
          for (int violationIndex = 0; violationIndex < fileViolations.getLength(); violationIndex++) {
            Element violationElement = (Element) fileViolations.item(violationIndex);
            String rule = violationElement.getAttribute("rule");
            String targetName = formatTarget(fileName, violationElement.getAttribute("beginline"), null);
            violations.add(new ValidationViolation(
                "lint.pmd." + sanitizeRuleId(trim(rule)),
                parsePmdSeverity(violationElement.getAttribute("priority")),
                "file",
                targetName,
                ValidationEngine.PMD,
                trim(violationElement.getTextContent()),
                "Address the PMD finding and rerun lint."
            ));
          }
        }
      } catch (Exception exception) {
        violations.add(parseFailure(
            ValidationEngine.PMD,
            "lint.pmd.report-parse-failed",
            report,
            exception.getMessage()
        ));
      }
    }
    return violations;
  }

  List<ValidationViolation> parseErrorProneDiagnostics(String output) {
    List<ValidationViolation> violations = new ArrayList<>();
    Matcher withColumn = ERROR_PRONE_WITH_COLUMN.matcher(output);
    while (withColumn.find()) {
      violations.add(errorProneViolation(withColumn.group(1), withColumn.group(2), withColumn.group(3), withColumn.group(4), withColumn.group(5)));
    }
    if (!violations.isEmpty()) {
      return violations;
    }

    Matcher lineOnly = ERROR_PRONE_LINE_ONLY.matcher(output);
    while (lineOnly.find()) {
      violations.add(errorProneViolation(lineOnly.group(1), lineOnly.group(2), null, lineOnly.group(3), lineOnly.group(4)));
    }
    return violations;
  }

  private ValidationViolation errorProneViolation(String file, String line, String column, String rule, String message) {
    return new ValidationViolation(
        "lint.error-prone." + sanitizeRuleId(rule),
        ValidationSeverity.ERROR,
        "file",
        formatTarget(file, line, column),
        ValidationEngine.ERROR_PRONE,
        trim(message),
        "Update source code to satisfy Error Prone diagnostics."
    );
  }

  private ValidationViolation parseFailure(
      ValidationEngine engine,
      String ruleId,
      Path target,
      String details
  ) {
    return new ValidationViolation(
        ruleId,
        ValidationSeverity.ERROR,
        "repository",
        target.toString(),
        engine,
        "Lint report parsing failed: " + trim(details),
        "Inspect lint report generation and parser compatibility."
    );
  }

  private Document readXml(Path path) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory.newDocumentBuilder().parse(path.toFile());
  }

  private ValidationSeverity mapCheckstyleSeverity(String rawSeverity) {
    return switch (trim(rawSeverity).toLowerCase()) {
      case "error" -> ValidationSeverity.ERROR;
      case "warning" -> ValidationSeverity.WARNING;
      case "info" -> ValidationSeverity.INFO;
      default -> null;
    };
  }

  private ValidationSeverity parsePmdSeverity(String rawPriority) {
    try {
      int priority = Integer.parseInt(rawPriority);
      return priority <= 2 ? ValidationSeverity.ERROR : ValidationSeverity.WARNING;
    } catch (NumberFormatException ignored) {
      return ValidationSeverity.WARNING;
    }
  }

  private String shortRuleName(String source, String fallback) {
    String normalized = trim(source);
    if (!normalized.contains(".")) {
      return normalized.isBlank() ? fallback : normalized;
    }
    String[] parts = normalized.split("\\.");
    return parts.length == 0 ? fallback : parts[parts.length - 1];
  }

  private String sanitizeRuleId(String value) {
    String normalized = trim(value).toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    return normalized.isBlank() ? "violation" : normalized;
  }

  private String formatTarget(String fileName, String line, String column) {
    String base = trim(fileName);
    String normalizedLine = trim(line);
    String normalizedColumn = trim(column);
    if (normalizedLine.isBlank()) {
      return base;
    }
    if (normalizedColumn.isBlank()) {
      return base + ":" + normalizedLine;
    }
    return base + ":" + normalizedLine + ":" + normalizedColumn;
  }

  private String trim(String value) {
    return value == null ? "" : value.trim();
  }
}

