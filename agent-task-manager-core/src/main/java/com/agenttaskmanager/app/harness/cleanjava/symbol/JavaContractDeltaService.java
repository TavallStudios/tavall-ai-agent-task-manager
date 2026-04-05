package com.agenttaskmanager.app.harness.cleanjava.symbol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class JavaContractDeltaService {

  public JavaContractDeltaReport compare(
      List<JavaClassProfile> baselineProfiles,
      List<JavaClassProfile> currentProfiles,
      List<String> changedSourcePaths,
      boolean reflectionAugmented
  ) {
    Map<String, JavaContractDigest> baselineDigests = digests(baselineProfiles);
    Map<String, JavaContractDigest> currentDigests = digests(currentProfiles);
    List<JavaContractChange> changes = new ArrayList<>();
    for (String className : baselineDigests.keySet()) {
      if (!currentDigests.containsKey(className)) {
        changes.add(new JavaContractChange("class-removed", className, "Class was removed from the contract surface.", true));
      }
    }
    for (String className : currentDigests.keySet()) {
      JavaContractDigest baseline = baselineDigests.get(className);
      JavaContractDigest current = currentDigests.get(className);
      if (baseline == null) {
        changes.add(new JavaContractChange("class-added", className, "Class was added.", false));
        continue;
      }
      compareList(changes, className, "class-modifiers", baseline.classModifiers(), current.classModifiers(), true);
      compareValue(changes, className, "super-class", baseline.superClass(), current.superClass(), true);
      compareList(changes, className, "interfaces", baseline.interfaces(), current.interfaces(), true);
      compareList(changes, className, "annotations", baseline.annotations(), current.annotations(), true);
      compareMembers(changes, className, "field", baseline.fields(), current.fields(), true);
      compareMembers(changes, className, "constructor", baseline.constructors(), current.constructors(), true);
      compareMembers(changes, className, "method", baseline.methods(), current.methods(), true);
      compareMembers(changes, className, "reference", baseline.referencedTypes(), current.referencedTypes(), false);
    }
    boolean risky = changes.stream().anyMatch(JavaContractChange::risky);
    String status = risky ? "failed" : "passed";
    String summary = changes.isEmpty()
        ? "No behavior-relevant Java contract deltas were detected."
        : "Java contract delta found " + changes.size() + " change(s); risky=" + risky + ".";
    return new JavaContractDeltaReport(
        status,
        risky,
        reflectionAugmented,
        changedSourcePaths == null ? List.of() : List.copyOf(changedSourcePaths),
        currentDigests.isEmpty() ? baselineDigests.keySet().stream().sorted().toList() : currentDigests.keySet().stream().sorted().toList(),
        List.copyOf(changes),
        summary
    );
  }

  public JavaContractDeltaReport skipped(String status, List<String> changedSourcePaths, String summary) {
    return new JavaContractDeltaReport(status, false, false, changedSourcePaths, List.of(), List.of(), summary);
  }

  public JavaContractDeltaReport parseFailure(List<String> changedSourcePaths, String summary) {
    JavaContractChange change = new JavaContractChange("source-parse-failure", "java-source", summary, true);
    return new JavaContractDeltaReport("failed", true, false, changedSourcePaths, List.of(), List.of(change), summary);
  }

  private Map<String, JavaContractDigest> digests(List<JavaClassProfile> profiles) {
    Map<String, JavaContractDigest> digests = new LinkedHashMap<>();
    JavaContractDigestFactory factory = new JavaContractDigestFactory();
    for (JavaClassProfile profile : profiles) {
      digests.put(profile.qualifiedName(), factory.create(profile));
    }
    return digests;
  }

  private void compareValue(
      List<JavaContractChange> changes,
      String className,
      String kind,
      String baseline,
      String current,
      boolean risky
  ) {
    String normalizedBaseline = baseline == null ? "" : baseline;
    String normalizedCurrent = current == null ? "" : current;
    if (!normalizedBaseline.equals(normalizedCurrent)) {
      changes.add(new JavaContractChange(kind, className, normalizedBaseline + " -> " + normalizedCurrent, risky));
    }
  }

  private void compareList(
      List<JavaContractChange> changes,
      String className,
      String kind,
      List<String> baseline,
      List<String> current,
      boolean risky
  ) {
    if (!List.copyOf(baseline).equals(List.copyOf(current))) {
      changes.add(new JavaContractChange(
          kind,
          className,
          "baseline=" + baseline + " current=" + current,
          risky
      ));
    }
  }

  private void compareMembers(
      List<JavaContractChange> changes,
      String className,
      String kind,
      List<String> baseline,
      List<String> current,
      boolean risky
  ) {
    for (String member : baseline) {
      if (!current.contains(member)) {
        changes.add(new JavaContractChange(kind + "-removed", className, member, risky));
      }
    }
    for (String member : current) {
      if (!baseline.contains(member)) {
        changes.add(new JavaContractChange(kind + "-added", className, member, false));
      }
    }
  }
}
