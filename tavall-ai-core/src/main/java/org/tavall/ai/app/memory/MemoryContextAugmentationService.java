package org.tavall.ai.app.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MemoryContextAugmentationService {

  private final List<MemoryKnowledgeProvider> providers;
  private final MemoryProviderTelemetryService telemetryService;

  public MemoryContextAugmentationService(
      List<MemoryKnowledgeProvider> providers,
      MemoryProviderTelemetryService telemetryService
  ) {
    this.providers = providers.stream()
        .sorted(Comparator.comparing(MemoryKnowledgeProvider::role)
            .thenComparing(MemoryKnowledgeProvider::providerId))
        .toList();
    this.telemetryService = telemetryService;
  }

  /** Retrieves replaceable structural and temporal knowledge for one memory hydration request. */
  public MemoryContextAugmentation augment(
      String projectId,
      String repoPath,
      String queryText,
      int limit,
      Map<String, Object> metadata
  ) {
    if (queryText == null || queryText.isBlank() || providers.isEmpty()) {
      return new MemoryContextAugmentation("", "", List.of());
    }
    MemoryKnowledgeQuery query = new MemoryKnowledgeQuery(projectId, repoPath, queryText, limit, metadata);
    List<MemoryKnowledgeContext> contexts = new ArrayList<>();
    for (MemoryKnowledgeProvider provider : providers) {
      MemoryKnowledgeContext context = provider.retrieve(query);
      telemetryService.record(context);
      contexts.add(context);
    }
    return new MemoryContextAugmentation(summary(contexts), section(contexts), contexts);
  }

  private String summary(List<MemoryKnowledgeContext> contexts) {
    long active = contexts.stream().filter(this::hasContext).count();
    long degraded = contexts.stream().filter(MemoryKnowledgeContext::degraded).count();
    if (active == 0L && degraded == 0L) {
      return "";
    }
    return " External knowledge retrieved " + active + " provider result(s) with " + degraded + " degraded provider(s).";
  }

  private String section(List<MemoryKnowledgeContext> contexts) {
    List<String> sections = contexts.stream()
        .filter(context -> hasContext(context) || context.degraded())
        .map(this::formatContext)
        .toList();
    return sections.isEmpty() ? "" : String.join("\n\n", sections);
  }

  private String formatContext(MemoryKnowledgeContext context) {
    String heading = switch (context.role()) {
      case STRUCTURAL -> "Structural code knowledge";
      case TEMPORAL -> "Temporal knowledge";
      case SEMANTIC -> "Semantic knowledge";
    };
    if (context.degraded()) {
      return heading + " (" + context.provider() + "): DEGRADED - " + context.error();
    }
    String evidence = context.evidenceReferences().isEmpty()
        ? ""
        : "\nEvidence references: " + context.evidenceReferences().stream().collect(Collectors.joining(", "));
    return heading + " (" + context.provider() + "):\n" + context.content() + evidence;
  }

  private boolean hasContext(MemoryKnowledgeContext context) {
    return context.content() != null && !context.content().isBlank();
  }
}
