package com.agenttaskmanager.app.retrieval;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SemanticContextClassifier {

  public SemanticClassification classify(String kind, String content, Map<String, Object> payload) {
    if (looksLikeDiff(kind, content, payload)) {
      return new SemanticClassification(SemanticCollectionDomain.CODE_REPO, SemanticContentType.DIFF);
    }
    if (looksLikeCode(kind, payload)) {
      return new SemanticClassification(SemanticCollectionDomain.CODE_REPO, SemanticContentType.CODE);
    }
    if (looksLikeChat(kind, payload)) {
      return new SemanticClassification(SemanticCollectionDomain.CHAT_ARTIFACT, SemanticContentType.CHAT);
    }
    if (looksLikeKnowledge(kind, payload)) {
      return new SemanticClassification(SemanticCollectionDomain.KNOWLEDGE_RULES, SemanticContentType.DOCUMENTATION);
    }
    if (looksLikeRunSummary(kind, payload)) {
      return new SemanticClassification(SemanticCollectionDomain.TASK_HISTORY, SemanticContentType.RUN_SUMMARY);
    }
    return new SemanticClassification(SemanticCollectionDomain.TASK_HISTORY, SemanticContentType.GENERIC);
  }

  private boolean looksLikeDiff(String kind, String content, Map<String, Object> payload) {
    if (containsAny(kind, "diff", "patch", "cleanup-review", "cleanup", "git")) {
      return true;
    }
    if (payload != null && (payload.containsKey("gitBase") || payload.containsKey("gitHead") || payload.containsKey("changedFiles"))) {
      return true;
    }
    String value = content == null ? "" : content.strip();
    return value.startsWith("diff --git") || value.contains("\n@@ ");
  }

  private boolean looksLikeCode(String kind, Map<String, Object> payload) {
    if (containsAny(kind, "code", "source", "implementation", "method", "class", "repo")) {
      return true;
    }
    if (payload == null) {
      return false;
    }
    return payload.containsKey("sourcePath")
        || payload.containsKey("className")
        || payload.containsKey("methodName")
        || payload.containsKey("filesChanged");
  }

  private boolean looksLikeChat(String kind, Map<String, Object> payload) {
    if (containsAny(kind, "chat", "transcript", "debug", "log", "screen", "artifact")) {
      return true;
    }
    if (payload == null) {
      return false;
    }
    return payload.containsKey("screenshot")
        || payload.containsKey("logs")
        || payload.containsKey("trace")
        || payload.containsKey("threadKey");
  }

  private boolean looksLikeKnowledge(String kind, Map<String, Object> payload) {
    if (containsAny(kind, "rules", "architecture", "examples", "docs", "knowledge")) {
      return true;
    }
    return payload != null && payload.containsKey("knowledgeBase");
  }

  private boolean looksLikeRunSummary(String kind, Map<String, Object> payload) {
    if (containsAny(kind, "summary", "decision", "review", "validation", "task")) {
      return true;
    }
    return payload != null && (payload.containsKey("summary") || payload.containsKey("decision") || payload.containsKey("validationStatus"));
  }

  private boolean containsAny(String rawValue, String... needles) {
    String value = rawValue == null ? "" : rawValue.strip().toLowerCase(Locale.ROOT);
    for (String needle : needles) {
      if (value.contains(needle)) {
        return true;
      }
    }
    return false;
  }
}
