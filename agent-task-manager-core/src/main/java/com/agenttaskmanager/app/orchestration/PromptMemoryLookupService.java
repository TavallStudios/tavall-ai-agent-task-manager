package com.agenttaskmanager.app.orchestration;

import com.agenttaskmanager.app.config.KnowledgeIndexProperties;
import com.agenttaskmanager.app.memory.MemoryHydration;
import com.agenttaskmanager.app.memory.MemoryRetrievalService;
import com.agenttaskmanager.app.model.PromptThreadDetail;
import com.agenttaskmanager.app.model.PromptThreadMemoryLookupResult;
import com.agenttaskmanager.app.model.orchestration.RetrievedSemanticContext;
import com.agenttaskmanager.app.persistence.postgres.PromptThreadRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PromptMemoryLookupService {

  private final KnowledgeIndexProperties knowledgeIndexProperties;
  private final MemoryRetrievalService memoryRetrievalService;
  private final PromptThreadRepository promptThreadRepository;
  private final SharedTaskContextService sharedTaskContextService;

  public PromptMemoryLookupService(
      KnowledgeIndexProperties knowledgeIndexProperties,
      MemoryRetrievalService memoryRetrievalService,
      PromptThreadRepository promptThreadRepository,
      SharedTaskContextService sharedTaskContextService
  ) {
    this.knowledgeIndexProperties = knowledgeIndexProperties;
    this.memoryRetrievalService = memoryRetrievalService;
    this.promptThreadRepository = promptThreadRepository;
    this.sharedTaskContextService = sharedTaskContextService;
  }

  public PromptMemorySnapshot lookup(String projectKey, String queryText) {
    PromptThreadMemoryLookupResult result = lookup(projectKey, null, queryText);
    return new PromptMemorySnapshot(result.summary(), result.section());
  }

  public PromptThreadMemoryLookupResult lookup(String projectKey, String threadKey, String queryText) {
    if (queryText == null || queryText.isBlank()) {
      PromptMemorySnapshot snapshot = new PromptMemorySnapshot(
          "Memory lookup skipped because the prompt was blank.",
          "No memory context was retrieved because the prompt was blank."
      );
      return new PromptThreadMemoryLookupResult(
          blank(threadKey),
          null,
          snapshot.summary(),
          snapshot.section(),
          List.of(),
          List.of(),
          List.of()
      );
    }
    try {
      Optional<PromptThreadDetail> exactThread = threadKey == null || threadKey.isBlank()
          ? Optional.empty()
          : promptThreadRepository.findDetail(threadKey);
      MemoryHydration hydration = memoryRetrievalService.lookup(
          projectKey,
          threadKey,
          "",
          "",
          "",
          "",
          queryText.strip(),
          Map.of("projectKey", blank(projectKey), "threadKey", blank(threadKey))
      );
      ThreadMemory threadMemory = mergeContexts(threadKey, hydration.semanticCandidates(), queryText.strip());
      List<RetrievedSemanticContext> merged = new ArrayList<>();
      merged.addAll(threadMemory.threadContexts());
      merged.addAll(threadMemory.projectContexts());
      merged.addAll(threadMemory.knowledgeContexts());
      PromptMemorySnapshot snapshot;
      if (merged.isEmpty() && hydration.exactRecords().isEmpty()) {
        snapshot = new PromptMemorySnapshot(
            "Memory lookup completed: no related entries found.",
            "No directly related memory entries were retrieved."
        );
      } else {
        snapshot = new PromptMemorySnapshot(
            buildSummary(exactThread.orElse(null), hydration, merged),
            buildSection(exactThread.orElse(null), hydration, merged)
        );
      }
      return new PromptThreadMemoryLookupResult(
          blank(threadKey),
          exactThread.map(PromptThreadDetail::thread).orElse(null),
          snapshot.summary(),
          snapshot.section(),
          threadMemory.threadContexts(),
          threadMemory.projectContexts(),
          threadMemory.knowledgeContexts()
      );
    } catch (RuntimeException exception) {
      String message = exception.getMessage() == null
          ? exception.getClass().getSimpleName()
          : exception.getMessage().strip();
      String summary = "Memory lookup failed: " + message;
      return new PromptThreadMemoryLookupResult(blank(threadKey), null, summary, summary, List.of(), List.of(), List.of());
    }
  }

  private ThreadMemory mergeContexts(String threadKey, List<RetrievedSemanticContext> semanticCandidates, String queryText) {
    List<RetrievedSemanticContext> threadContexts = new ArrayList<>();
    List<RetrievedSemanticContext> projectContexts = new ArrayList<>(semanticCandidates);
    List<RetrievedSemanticContext> knowledgeContexts = new ArrayList<>();
    if (threadKey != null && !threadKey.isBlank()) {
      threadContexts.addAll(semanticCandidates.stream()
          .filter(context -> threadKey.equals(readPayloadValue(context.payload(), "threadKey")))
          .toList());
      projectContexts = semanticCandidates.stream()
          .filter(context -> !threadKey.equals(readPayloadValue(context.payload(), "threadKey")))
          .toList();
    }
    if (knowledgeIndexProperties.isEnabled()) {
      knowledgeContexts.addAll(sharedTaskContextService.searchKnowledgeContexts(
          knowledgeIndexProperties.getKnowledgeBase(),
          queryText,
          knowledgeIndexProperties.getPromptResultLimit()
      ));
    }
    List<RetrievedSemanticContext> combined = new ArrayList<>();
    combined.addAll(threadContexts);
    combined.addAll(projectContexts);
    combined.addAll(knowledgeContexts);
    Map<String, RetrievedSemanticContext> deduped = new LinkedHashMap<>();
    combined.stream()
        .sorted(Comparator.comparingDouble(RetrievedSemanticContext::score).reversed())
        .forEach(context -> deduped.putIfAbsent(context.id(), context));
    return new ThreadMemory(
        dedupe(threadContexts, deduped),
        dedupe(projectContexts, deduped),
        dedupe(knowledgeContexts, deduped)
    );
  }

  private String buildSummary(
      PromptThreadDetail exactThread,
      MemoryHydration hydration,
      List<RetrievedSemanticContext> contexts
  ) {
    StringBuilder builder = new StringBuilder("Memory lookup completed.");
    appendThreadMatchSummary(builder, exactThread);
    builder.append(" Exact memory records=").append(hydration.exactRecords().size()).append(".");
    builder.append(" Related entries:\n");
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

  private String buildSection(
      PromptThreadDetail exactThread,
      MemoryHydration hydration,
      List<RetrievedSemanticContext> contexts
  ) {
    StringBuilder builder = new StringBuilder();
    appendThreadMatchSection(builder, exactThread);
    if (!hydration.section().isBlank()) {
      builder.append(hydration.section()).append("\n");
    }
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

  private void appendThreadMatchSummary(StringBuilder builder, PromptThreadDetail exactThread) {
    if (exactThread == null) {
      return;
    }
    builder.append(" Matched thread key ")
        .append(exactThread.thread().threadKey())
        .append(" with ")
        .append(exactThread.requests().size())
        .append(" requests and ")
        .append(exactThread.messages().size())
        .append(" persisted messages.");
  }

  private void appendThreadMatchSection(StringBuilder builder, PromptThreadDetail exactThread) {
    if (exactThread == null) {
      return;
    }
    builder.append("Exact thread match: ")
        .append(exactThread.thread().threadKey())
        .append(", latestSummary=")
        .append(exactThread.thread().latestRequestSummary() == null ? "" : exactThread.thread().latestRequestSummary())
        .append("\n");
    exactThread.messages().stream()
        .skip(Math.max(0, exactThread.messages().size() - 5))
        .forEach(message -> builder.append("recent[")
            .append(message.messageKind())
            .append("] ")
            .append(snippet(message.body(), 220))
            .append("\n"));
  }

  private List<RetrievedSemanticContext> dedupe(
      List<RetrievedSemanticContext> source,
      Map<String, RetrievedSemanticContext> deduped
  ) {
    return source.stream()
        .map(context -> deduped.get(context.id()))
        .filter(java.util.Objects::nonNull)
        .distinct()
        .toList();
  }

  private String blank(String value) {
    return value == null ? "" : value;
  }

  public record PromptMemorySnapshot(String summary, String section) {
  }

  private record ThreadMemory(
      List<RetrievedSemanticContext> threadContexts,
      List<RetrievedSemanticContext> projectContexts,
      List<RetrievedSemanticContext> knowledgeContexts
  ) {
  }
}
