package com.agenttaskmanager.app.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MemoryPolicyService {

  public List<MemoryMutationPlan> evaluate(
      MemoryIdentity identity,
      String requestText,
      String responseText,
      boolean failed
  ) {
    String effectiveRequest = blank(requestText);
    String effectiveResponse = blank(responseText);
    List<MemoryMutationPlan> plans = new ArrayList<>();
    String combined = (effectiveRequest + "\n" + effectiveResponse).strip();
    if (combined.isBlank()) {
      return List.of(noopPlan(identity));
    }
    plans.add(workingMemoryPlan(identity, effectiveRequest, effectiveResponse, failed));
    addOptionalPlan(plans, preferencePlan(identity, combined));
    addOptionalPlan(plans, profilePlan(identity, combined));
    addOptionalPlan(plans, projectStatePlan(identity, combined));
    addOptionalPlan(plans, correctionPlan(identity, combined));
    addOptionalPlan(plans, taskPlan(identity, combined));
    if (plans.size() == 1 && !failed && combined.length() > 60 && looksMemorable(combined)) {
      addOptionalPlan(plans, episodicPlan(identity, combined));
    }
    return plans;
  }

  private MemoryMutationPlan workingMemoryPlan(
      MemoryIdentity identity,
      String requestText,
      String responseText,
      boolean failed
  ) {
    MemoryScope scope = identity.threadKey().isBlank() ? MemoryScope.PROJECT : MemoryScope.SESSION;
    Set<String> facts = new LinkedHashSet<>();
    if (!requestText.isBlank()) {
      facts.add("latest_request=" + summarize(requestText, 180));
    }
    if (!responseText.isBlank()) {
      facts.add("latest_response=" + summarize(responseText, 180));
    }
    if (failed) {
      facts.add("latest_status=failed");
    }
    return new MemoryMutationPlan(
        MemoryAction.UPDATE_EXISTING_MEMORY,
        scope,
        MemoryKind.WORKING_MEMORY,
        "Current working memory",
        normalizeKey("current-working-memory"),
        summarize(requestText + " " + responseText, 220),
        List.copyOf(facts),
        70,
        "internal",
        "implicit",
        Map.of(
            "threadKey", identity.threadKey(),
            "projectId", identity.projectId(),
            "chatId", identity.chatId(),
            "status", failed ? "failed" : "completed"
        )
    );
  }

  private MemoryMutationPlan preferencePlan(MemoryIdentity identity, String combined) {
    String match = extractPhrase(combined, "prefer", "please", "always", "never", "don't");
    if (match.isBlank()) {
      return null;
    }
    return new MemoryMutationPlan(
        MemoryAction.UPSERT_SEMANTIC_MEMORY,
        MemoryScope.GLOBAL,
        MemoryKind.PREFERENCE,
        "Preference: " + summarize(match, 80),
        normalizeKey("preference:" + match),
        summarize(match, 180),
        List.of(match),
        85,
        "internal",
        "implicit",
        Map.of("classification", "preference")
    );
  }

  private MemoryMutationPlan profilePlan(MemoryIdentity identity, String combined) {
    String match = extractPhrase(combined, "my name", "i am", "i use", "we use");
    if (match.isBlank()) {
      return null;
    }
    return new MemoryMutationPlan(
        MemoryAction.UPSERT_SEMANTIC_MEMORY,
        MemoryScope.GLOBAL,
        MemoryKind.PROFILE,
        "Profile: " + summarize(match, 80),
        normalizeKey("profile:" + match),
        summarize(match, 180),
        List.of(match),
        75,
        "internal",
        "implicit",
        Map.of("classification", "profile")
    );
  }

  private MemoryMutationPlan projectStatePlan(MemoryIdentity identity, String combined) {
    if (identity.projectId().isBlank() || !containsAny(combined, "status", "blocked", "deployed", "migration", "schema", "pipeline")) {
      return null;
    }
    String summary = summarize(combined, 200);
    return new MemoryMutationPlan(
        MemoryAction.UPSERT_SEMANTIC_MEMORY,
        MemoryScope.PROJECT,
        MemoryKind.PROJECT_STATE,
        "Project state",
        normalizeKey("project-state:" + identity.projectId()),
        summary,
        splitFacts(combined),
        80,
        "internal",
        "implicit",
        Map.of("classification", "project-state")
    );
  }

  private MemoryMutationPlan correctionPlan(MemoryIdentity identity, String combined) {
    if (!containsAny(combined, "actually", "correction", "instead")) {
      return null;
    }
    String summary = summarize(combined, 180);
    return new MemoryMutationPlan(
        MemoryAction.SUPERSEDE_MEMORY,
        identity.projectId().isBlank() ? MemoryScope.GLOBAL : MemoryScope.PROJECT,
        MemoryKind.CORRECTION,
        "Correction",
        normalizeKey("correction:" + summary),
        summary,
        splitFacts(combined),
        90,
        "internal",
        "implicit",
        Map.of("classification", "correction")
    );
  }

  private MemoryMutationPlan taskPlan(MemoryIdentity identity, String combined) {
    if (!containsAny(combined, "todo", "next", "follow up", "task", "need to")) {
      return null;
    }
    String summary = summarize(combined, 180);
    return new MemoryMutationPlan(
        MemoryAction.CLOSE_TASK,
        identity.projectId().isBlank() ? MemoryScope.SESSION : MemoryScope.PROJECT,
        MemoryKind.TASK,
        "Task continuity",
        normalizeKey("task:" + identity.threadKey() + ":" + summary),
        summary,
        splitFacts(combined),
        78,
        "internal",
        "implicit",
        Map.of("classification", "task")
    );
  }

  private MemoryMutationPlan episodicPlan(MemoryIdentity identity, String combined) {
    return new MemoryMutationPlan(
        MemoryAction.CREATE_EPISODIC_MEMORY,
        identity.projectId().isBlank() ? MemoryScope.SESSION : MemoryScope.PROJECT,
        MemoryKind.EPISODIC,
        "Episode",
        normalizeKey("episode:" + combined.substring(0, Math.min(80, combined.length()))),
        summarize(combined, 180),
        splitFacts(combined),
        60,
        "internal",
        "implicit",
        Map.of("classification", "episodic")
    );
  }

  private MemoryMutationPlan noopPlan(MemoryIdentity identity) {
    return new MemoryMutationPlan(
        MemoryAction.NOOP,
        identity.projectId().isBlank() ? MemoryScope.SESSION : MemoryScope.PROJECT,
        MemoryKind.WORKING_MEMORY,
        "No-op memory evaluation",
        normalizeKey("noop"),
        "Memory evaluation completed with no canonical mutation.",
        List.of(),
        0,
        "internal",
        "implicit",
        Map.of()
    );
  }

  private void addOptionalPlan(List<MemoryMutationPlan> plans, MemoryMutationPlan plan) {
    if (plan != null) {
      plans.add(plan);
    }
  }

  private boolean looksMemorable(String value) {
    return value.split("\\s+").length >= 12;
  }

  private String extractPhrase(String text, String... needles) {
    String lower = text.toLowerCase(Locale.ROOT);
    for (String needle : needles) {
      int index = lower.indexOf(needle);
      if (index >= 0) {
        int end = Math.min(text.length(), index + 160);
        return text.substring(index, end).strip();
      }
    }
    return "";
  }

  private boolean containsAny(String text, String... needles) {
    String lower = blank(text).toLowerCase(Locale.ROOT);
    for (String needle : needles) {
      if (lower.contains(needle)) {
        return true;
      }
    }
    return false;
  }

  private List<String> splitFacts(String text) {
    Set<String> facts = new LinkedHashSet<>();
    for (String part : text.split("[\\n\\.!?]")) {
      String normalized = part.replaceAll("\\s+", " ").strip();
      if (!normalized.isBlank()) {
        facts.add(summarize(normalized, 140));
      }
      if (facts.size() == 4) {
        break;
      }
    }
    return List.copyOf(facts);
  }

  private String summarize(String value, int limit) {
    String normalized = blank(value).replaceAll("\\s+", " ");
    if (normalized.length() <= limit) {
      return normalized;
    }
    return normalized.substring(0, limit - 3) + "...";
  }

  private String normalizeKey(String value) {
    return blank(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
  }

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }
}
