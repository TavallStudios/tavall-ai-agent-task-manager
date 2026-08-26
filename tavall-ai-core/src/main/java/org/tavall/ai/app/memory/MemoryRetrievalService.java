package org.tavall.ai.app.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.tavall.ai.app.config.KnowledgeIndexProperties;
import org.tavall.ai.app.config.MemoryRuntimeProperties;
import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;
import org.tavall.ai.app.persistence.postgres.MemoryRecordRepository;
import org.tavall.ai.app.persistence.redis.MemoryRuntimeHotStateStore;
import org.tavall.ai.app.retrieval.QdrantHealthService;
import org.tavall.ai.app.retrieval.SemanticMemoryService;

@Service
public class MemoryRetrievalService {

  private final MemoryRuntimeProperties properties;
  private final KnowledgeIndexProperties knowledgeIndexProperties;
  private final MemoryIdentityResolver identityResolver;
  private final MemoryRecordRepository memoryRecordRepository;
  private final MemoryRuntimeHotStateStore hotStateStore;
  private final SemanticMemoryService semanticMemoryService;
  private final MemoryContextAugmentationService contextAugmentationService;
  private final MemoryProviderTelemetryService telemetryService;
  private final QdrantHealthService qdrantHealthService;

  public MemoryRetrievalService(
      MemoryRuntimeProperties properties,
      KnowledgeIndexProperties knowledgeIndexProperties,
      MemoryIdentityResolver identityResolver,
      MemoryRecordRepository memoryRecordRepository,
      MemoryRuntimeHotStateStore hotStateStore,
      SemanticMemoryService semanticMemoryService,
      MemoryContextAugmentationService contextAugmentationService,
      MemoryProviderTelemetryService telemetryService,
      QdrantHealthService qdrantHealthService
  ) {
    this.properties = properties;
    this.knowledgeIndexProperties = knowledgeIndexProperties;
    this.identityResolver = identityResolver;
    this.memoryRecordRepository = memoryRecordRepository;
    this.hotStateStore = hotStateStore;
    this.semanticMemoryService = semanticMemoryService;
    this.contextAugmentationService = contextAugmentationService;
    this.telemetryService = telemetryService;
    this.qdrantHealthService = qdrantHealthService;
  }

  /** Resolves exact, semantic, structural, temporal, and configured knowledge context once per interaction. */
  public MemoryHydration lookup(
      String projectId,
      String threadKey,
      String sessionId,
      String requestedBy,
      String requestedFrom,
      String repoPath,
      String queryText,
      Map<String, Object> metadata
  ) {
    MemoryIdentity identity = identityResolver.resolve(
        projectId,
        threadKey,
        sessionId,
        requestedBy,
        requestedFrom,
        repoPath,
        metadata
    );
    List<MemoryRecord> exactRecords = loadExactState(identity);
    String normalizedQuery = blank(queryText);
    long semanticStarted = System.nanoTime();
    List<RetrievedSemanticContext> semanticCandidates;
    RuntimeException semanticFailure = null;
    if (normalizedQuery.isBlank() || identity.projectId().isBlank()) {
      semanticCandidates = List.of();
    } else {
      try {
        semanticCandidates = searchSemantic(identity, normalizedQuery, true);
      } catch (RuntimeException exception) {
        semanticFailure = exception;
        semanticCandidates = List.of();
      }
    }
    MemoryKnowledgeContext semanticStatus = semanticStatus(
        semanticCandidates.size(),
        semanticFailure,
        semanticStarted
    );
    if (!normalizedQuery.isBlank() && !identity.projectId().isBlank()) {
      telemetryService.record(semanticStatus);
    }
    MemoryContextAugmentation augmentation = contextAugmentationService.augment(
        identity.projectId(),
        repoPath,
        normalizedQuery,
        properties.getExternalContextLimit(),
        metadata
    );
    List<MemoryKnowledgeContext> providerContexts = new ArrayList<>(augmentation.contexts());
    if (!normalizedQuery.isBlank() && !identity.projectId().isBlank()) {
      providerContexts.add(semanticStatus);
    }
    return new MemoryHydration(
        buildSummary(exactRecords, semanticCandidates, semanticStatus) + augmentation.summary(),
        appendSection(buildSection(exactRecords, semanticCandidates, semanticStatus), augmentation.section()),
        exactRecords,
        semanticCandidates,
        providerContexts
    );
  }

  public MemoryIdentity resolveIdentity(
      String projectId,
      String threadKey,
      String sessionId,
      String requestedBy,
      String requestedFrom,
      String repoPath,
      Map<String, Object> metadata
  ) {
    return identityResolver.resolve(projectId, threadKey, sessionId, requestedBy, requestedFrom, repoPath, metadata);
  }

  public List<MemoryRecord> loadExactState(MemoryIdentity identity) {
    String cacheKey = exactStateCacheKey(identity);
    return hotStateStore.loadWorkingMemory(cacheKey)
        .flatMap(serialized -> hotStateStore.readJson(serialized, new TypeReference<List<MemoryRecord>>() {
        }))
        .orElseGet(() -> refreshExactState(identity));
  }

  public List<MemoryRecord> refreshExactState(MemoryIdentity identity) {
    List<MemoryRecord> records = memoryRecordRepository.loadExactState(identity, properties.getExactStateLimit());
    hotStateStore.storeWorkingMemory(exactStateCacheKey(identity), records, properties.getHotStateTtl());
    return records;
  }

  public List<MemoryRecord> refreshExactStateAfterWrite(MemoryIdentity identity, MemoryScope scope) {
    if (scope == MemoryScope.GLOBAL) {
      hotStateStore.incrementWorkingMemoryRevision(authorityKey(identity));
    }
    return refreshExactState(identity);
  }

  public List<RetrievedSemanticContext> searchSemantic(MemoryIdentity identity, String queryText) {
    return searchSemantic(identity, queryText, false);
  }

  private List<RetrievedSemanticContext> searchSemantic(
      MemoryIdentity identity,
      String queryText,
      boolean strict
  ) {
    if (queryText.isBlank() || identity.projectId().isBlank()) {
      return List.of();
    }
    Map<String, Object> payloadFilter = new LinkedHashMap<>();
    if (!blank(identity.userId()).isBlank()) {
      payloadFilter.put("userId", blank(identity.userId()));
    }
    if (!blank(identity.workspaceId()).isBlank()) {
      payloadFilter.put("workspaceId", blank(identity.workspaceId()));
    }
    payloadFilter.put("status", "active");
    payloadFilter.put("tombstoned", false);

    List<RetrievedSemanticContext> contexts = new ArrayList<>();
    contexts.addAll(strict
        ? semanticMemoryService.searchProjectStrict(
            identity.projectId(), queryText, properties.getSemanticCandidateLimit(), payloadFilter)
        : semanticMemoryService.searchProject(
            identity.projectId(), queryText, properties.getSemanticCandidateLimit(), payloadFilter));

    if (knowledgeIndexProperties.isEnabled()) {
      int knowledgeLimit = Math.max(1, knowledgeIndexProperties.getPromptResultLimit());
      contexts.addAll(strict
          ? semanticMemoryService.searchKnowledgeStrict(
              knowledgeIndexProperties.getKnowledgeBase(), queryText, knowledgeLimit, Map.of())
          : semanticMemoryService.searchKnowledge(
              knowledgeIndexProperties.getKnowledgeBase(), queryText, knowledgeLimit, Map.of()));
    }

    Map<String, RetrievedSemanticContext> deduped = new LinkedHashMap<>();
    contexts.stream()
        .sorted(Comparator.comparingDouble(this::compositeScore).reversed())
        .forEach(context -> deduped.putIfAbsent(semanticIdentity(context), context));
    return deduped.values().stream()
        .sorted(Comparator.comparingDouble(this::compositeScore).reversed())
        .limit(properties.getSemanticCandidateLimit())
        .toList();
  }

  private String exactStateCacheKey(MemoryIdentity identity) {
    long globalRevision = hotStateStore.workingMemoryRevision(authorityKey(identity));
    return identity.cacheKey() + "|global-revision=" + globalRevision;
  }

  private String authorityKey(MemoryIdentity identity) {
    return blank(identity.userId()) + "|" + blank(identity.workspaceId());
  }

  private String semanticIdentity(RetrievedSemanticContext context) {
    String scope = stringValue(context.payload(), "knowledgeBase");
    if (scope.isBlank()) {
      scope = stringValue(context.payload(), "projectKey");
    }
    return scope + ":" + context.id();
  }

  private double compositeScore(RetrievedSemanticContext context) {
    Map<String, Object> payload = context.payload();
    double semantic = context.score();
    double recency = recencyBoost(payload);
    double importance = numeric(payload, "importance") / 100.0D;
    double scope = scopeBoost(payload);
    return (semantic * 0.65D) + (recency * 0.15D) + (importance * 0.15D) + (scope * 0.05D);
  }

  private double recencyBoost(Map<String, Object> payload) {
    String updatedAt = stringValue(payload, "updatedAt");
    if (updatedAt.isBlank()) {
      return 0.1D;
    }
    try {
      long days = Math.max(0L, ChronoUnit.DAYS.between(OffsetDateTime.parse(updatedAt), OffsetDateTime.now()));
      return 1.0D / (1.0D + days);
    } catch (RuntimeException exception) {
      return 0.1D;
    }
  }

  private double scopeBoost(Map<String, Object> payload) {
    return switch (stringValue(payload, "scope")) {
      case "SESSION" -> 1.0D;
      case "PROJECT" -> 0.6D;
      case "GLOBAL" -> 0.3D;
      default -> 0.1D;
    };
  }

  private double numeric(Map<String, Object> payload, String key) {
    Object value = payload.get(key);
    return value instanceof Number number ? number.doubleValue() : 0.0D;
  }

  private String buildSummary(
      List<MemoryRecord> exactRecords,
      List<RetrievedSemanticContext> semanticCandidates,
      MemoryKnowledgeContext semanticStatus
  ) {
    long knowledgeCount = semanticCandidates.stream()
        .filter(context -> !stringValue(context.payload(), "knowledgeBase").isBlank())
        .count();
    return "Memory pipeline retrieved "
        + exactRecords.size()
        + " exact records and "
        + semanticCandidates.size()
        + " semantic candidates (knowledge="
        + knowledgeCount
        + "). Semantic provider qdrant="
        + semanticStatus.metadata().getOrDefault("status", "UNKNOWN")
        + ".";
  }

  private String buildSection(
      List<MemoryRecord> exactRecords,
      List<RetrievedSemanticContext> semanticCandidates,
      MemoryKnowledgeContext semanticStatus
  ) {
    String exactSection = exactRecords.isEmpty()
        ? "Exact working memory: none."
        : "Exact working memory:\n" + exactRecords.stream()
            .map(record -> "- [" + record.kind().name().toLowerCase() + "] " + record.title() + ": " + record.summary())
            .collect(Collectors.joining("\n"));
    String semanticSection = semanticCandidates.isEmpty()
        ? "Semantic recall: none."
        : "Semantic recall:\n" + semanticCandidates.stream()
            .map(candidate -> "- score="
                + String.format("%.3f", compositeScore(candidate))
                + " source="
                + semanticSource(candidate)
                + " chunk="
                + summarize(stringValue(candidate.payload(), "chunkText"), 220))
            .collect(Collectors.joining("\n"));
    String providerSection = "Semantic provider status: qdrant="
        + semanticStatus.metadata().getOrDefault("status", "UNKNOWN");
    if (semanticStatus.degraded() && !semanticStatus.error().isBlank()) {
      providerSection += " (" + semanticStatus.error() + ")";
    }
    return exactSection + "\n\n" + semanticSection + "\n\n" + providerSection;
  }

  private String semanticSource(RetrievedSemanticContext context) {
    String knowledgeBase = stringValue(context.payload(), "knowledgeBase");
    if (!knowledgeBase.isBlank()) {
      return "knowledge:" + knowledgeBase + "/" + stringValue(context.payload(), "kind");
    }
    return stringValue(context.payload(), "kind");
  }

  private MemoryKnowledgeContext semanticStatus(
      int resultCount,
      RuntimeException failure,
      long started
  ) {
    QdrantHealthService.Snapshot snapshot = qdrantHealthService.currentSnapshot();
    boolean degraded = failure != null || (snapshot.configured() && !snapshot.writeThroughHealthy());
    String error = failure == null ? (degraded ? snapshot.summary() : "") : message(failure);
    return new MemoryKnowledgeContext(
        "qdrant",
        MemoryKnowledgeRole.SEMANTIC,
        "",
        List.of(),
        Map.of(
            "configured", snapshot.configured(),
            "status", snapshot.configured() ? snapshot.status() : "LOCAL_FALLBACK",
            "localFallback", !snapshot.configured(),
            "resultCount", resultCount,
            "knowledgeEnabled", knowledgeIndexProperties.isEnabled()
        ),
        Math.max(0L, (System.nanoTime() - started) / 1_000_000L),
        degraded,
        error
    );
  }

  private String message(RuntimeException exception) {
    return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
  }

  private String appendSection(String base, String extra) {
    if (extra == null || extra.isBlank()) {
      return base;
    }
    return base + "\n\n" + extra;
  }

  private String stringValue(Map<String, Object> payload, String key) {
    Object value = payload == null ? null : payload.get(key);
    return value == null ? "" : String.valueOf(value).strip();
  }

  private String summarize(String value, int limit) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String normalized = value.replaceAll("\\s+", " ").strip();
    if (normalized.length() <= limit) {
      return normalized;
    }
    return normalized.substring(0, limit - 3) + "...";
  }

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }
}
