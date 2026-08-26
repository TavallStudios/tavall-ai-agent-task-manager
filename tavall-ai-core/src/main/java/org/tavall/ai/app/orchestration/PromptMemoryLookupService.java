package org.tavall.ai.app.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.tavall.ai.app.memory.MemoryHydration;
import org.tavall.ai.app.memory.MemoryRetrievalService;
import org.tavall.ai.app.model.PromptThreadDetail;
import org.tavall.ai.app.model.PromptThreadMemoryLookupResult;
import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;
import org.tavall.ai.app.persistence.postgres.PromptThreadRepository;

@Service
public class PromptMemoryLookupService {

  private final MemoryRetrievalService memoryRetrievalService;
  private final PromptThreadRepository promptThreadRepository;

  public PromptMemoryLookupService(
      MemoryRetrievalService memoryRetrievalService,
      PromptThreadRepository promptThreadRepository
  ) {
    this.memoryRetrievalService = memoryRetrievalService;
    this.promptThreadRepository = promptThreadRepository;
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
      ThreadMemory views = partition(threadKey, hydration.semanticCandidates());
      String summary = prependThreadSummary(exactThread.orElse(null), hydration.summary());
      String section = prependThreadSection(exactThread.orElse(null), hydration.section());
      return new PromptThreadMemoryLookupResult(
          blank(threadKey),
          exactThread.map(PromptThreadDetail::thread).orElse(null),
          summary,
          section,
          views.threadContexts(),
          views.projectContexts(),
          views.knowledgeContexts()
      );
    } catch (RuntimeException exception) {
      String message = exception.getMessage() == null
          ? exception.getClass().getSimpleName()
          : exception.getMessage().strip();
      String summary = "Memory lookup failed: " + message;
      return new PromptThreadMemoryLookupResult(blank(threadKey), null, summary, summary, List.of(), List.of(), List.of());
    }
  }

  private ThreadMemory partition(String threadKey, List<RetrievedSemanticContext> semanticCandidates) {
    List<RetrievedSemanticContext> threadContexts = new ArrayList<>();
    List<RetrievedSemanticContext> projectContexts = new ArrayList<>();
    List<RetrievedSemanticContext> knowledgeContexts = new ArrayList<>();
    for (RetrievedSemanticContext context : semanticCandidates) {
      if (!readPayloadValue(context.payload(), "knowledgeBase").isBlank()) {
        knowledgeContexts.add(context);
      } else if (threadKey != null
          && !threadKey.isBlank()
          && threadKey.equals(readPayloadValue(context.payload(), "threadKey"))) {
        threadContexts.add(context);
      } else {
        projectContexts.add(context);
      }
    }
    return new ThreadMemory(
        List.copyOf(threadContexts),
        List.copyOf(projectContexts),
        List.copyOf(knowledgeContexts)
    );
  }

  private String prependThreadSummary(PromptThreadDetail exactThread, String hydrationSummary) {
    if (exactThread == null) {
      return hydrationSummary;
    }
    return "Matched thread key "
        + exactThread.thread().threadKey()
        + " with "
        + exactThread.requests().size()
        + " requests and "
        + exactThread.messages().size()
        + " persisted messages. "
        + hydrationSummary;
  }

  private String prependThreadSection(PromptThreadDetail exactThread, String hydrationSection) {
    if (exactThread == null) {
      return hydrationSection;
    }
    StringBuilder builder = new StringBuilder();
    builder.append("Exact prompt-thread audit match: ")
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
    if (hydrationSection != null && !hydrationSection.isBlank()) {
      builder.append("\n").append(hydrationSection);
    }
    return builder.toString().strip();
  }

  private static String readPayloadValue(Map<String, Object> payload, String key) {
    Object value = payload == null ? null : payload.get(key);
    return value == null ? "" : String.valueOf(value).strip();
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
