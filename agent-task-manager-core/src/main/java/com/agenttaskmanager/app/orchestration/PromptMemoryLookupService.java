package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.config.KnowledgeIndexProperties;
import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PromptMemoryLookupService {

  private static final int TASK_MEMORY_LIMIT = 5;
  private final SharedTaskContextService sharedTaskContextService;
  private final KnowledgeIndexProperties knowledgeIndexProperties;

  public PromptMemoryLookupService(
      SharedTaskContextService sharedTaskContextService,
      KnowledgeIndexProperties knowledgeIndexProperties
  ) {
    this.sharedTaskContextService = sharedTaskContextService;
    this.knowledgeIndexProperties = knowledgeIndexProperties;
  }

  public PromptMemorySnapshot lookup(String projectKey, String queryText) {
    if (queryText == null || queryText.isBlank()) {
      return new PromptMemorySnapshot(
          "Memory lookup skipped because the prompt was blank.",
          "No memory context was retrieved because the prompt was blank."
      );
    }
    try {
      List<RetrievedSemanticContext> merged = mergeContexts(projectKey, queryText.strip());
      if (merged.isEmpty()) {
        return new PromptMemorySnapshot(
            "Memory lookup completed: no related entries found.",
            "No directly related memory entries were retrieved."
        );
      }
      return new PromptMemorySnapshot(buildSummary(merged), buildSection(merged));
    } catch (RuntimeException exception) {
      String message = exception.getMessage() == null
          ? exception.getClass().getSimpleName()
          : exception.getMessage().strip();
      String summary = "Memory lookup failed: " + message;
      return new PromptMemorySnapshot(summary, summary);
    }
  }

  private List<RetrievedSemanticContext> mergeContexts(String projectKey, String queryText) {
    List<RetrievedSemanticContext> combined = new ArrayList<>();
    if (projectKey != null && !projectKey.isBlank()) {
      combined.addAll(sharedTaskContextService.searchProjectRelatedContexts(projectKey, queryText, TASK_MEMORY_LIMIT));
    }
    if (knowledgeIndexProperties.isEnabled()) {
      combined.addAll(sharedTaskContextService.searchKnowledgeContexts(
          knowledgeIndexProperties.getKnowledgeBase(),
          queryText,
          knowledgeIndexProperties.getPromptResultLimit()
      ));
    }
    Map<String, RetrievedSemanticContext> deduped = new LinkedHashMap<>();
    combined.stream()
        .sorted(Comparator.comparingDouble(RetrievedSemanticContext::score).reversed())
        .forEach(context -> deduped.putIfAbsent(context.id(), context));
    return List.copyOf(deduped.values());
  }

  private String buildSummary(List<RetrievedSemanticContext> contexts) {
    StringBuilder builder = new StringBuilder("Memory lookup completed. Related entries:\n");
    for (int index = 0; index < contexts.size(); index++) {
      RetrievedSemanticContext context = contexts.get(index);
      builder.append(index + 1)
          .append(". score=")
          .append(String.format("%.3f", context.score()))
          .append(", source=")
          .append(describeSource(context.payload()))
          .append("\n   ")
          .append(snippet(readChunkText(context.payload()), 220))
          .append("\n");
    }
    return builder.toString().strip();
  }

  private String buildSection(List<RetrievedSemanticContext> contexts) {
    StringBuilder builder = new StringBuilder();
    for (int index = 0; index < contexts.size(); index++) {
      RetrievedSemanticContext context = contexts.get(index);
      Map<String, Object> payload = context.payload();
      builder.append(index + 1)
          .append(". score=")
          .append(String.format("%.3f", context.score()))
          .append(", source=")
          .append(describeSource(payload));
      String taskId = readPayloadValue(payload, "taskId");
      String sourcePath = readPayloadValue(payload, "sourcePath");
      if (!taskId.isBlank()) {
        builder.append(", taskId=").append(taskId);
      }
      if (!sourcePath.isBlank()) {
        builder.append(", sourcePath=").append(sourcePath);
      }
      builder.append("\n   chunk: ")
          .append(snippet(readChunkText(payload), 400))
          .append("\n");
    }
    return builder.toString().strip();
  }

  private static String describeSource(Map<String, Object> payload) {
    String knowledgeBase = readPayloadValue(payload, "knowledgeBase");
    if (!knowledgeBase.isBlank()) {
      return knowledgeBase;
    }
    String projectKey = readPayloadValue(payload, "projectKey");
    if (!projectKey.isBlank()) {
      return projectKey + "/" + readPayloadValue(payload, "kind");
    }
    return readPayloadValue(payload, "kind");
  }

  private static String readPayloadValue(Map<String, Object> payload, String key) {
    Object value = payload == null ? null : payload.get(key);
    return value == null ? "" : String.valueOf(value).strip();
  }

  private static String readChunkText(Map<String, Object> payload) {
    String chunkText = readPayloadValue(payload, "chunkText");
    if (!chunkText.isBlank()) {
      return chunkText;
    }
    return readPayloadValue(payload, "body");
  }

  private static String snippet(String value, int maxLength) {
    if (value == null) {
      return "";
    }
    String normalized = value.replaceAll("\\s+", " ").strip();
    if (normalized.length() <= maxLength) {
      return normalized;
    }
    return normalized.substring(0, maxLength - 3) + "...";
  }

  public record PromptMemorySnapshot(String summary, String section) {
  }
}
