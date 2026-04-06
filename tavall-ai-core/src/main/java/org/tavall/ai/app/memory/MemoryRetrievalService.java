package org.tavall.ai.app.memory;

import org.tavall.ai.app.config.MemoryRuntimeProperties;
import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;
import org.tavall.ai.app.orchestration.SharedTaskContextService;
import org.tavall.ai.app.persistence.redis.MemoryRuntimeHotStateStore;
import org.tavall.ai.app.persistence.postgres.MemoryRecordRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MemoryRetrievalService {

  private final MemoryRuntimeProperties properties;
  private final MemoryIdentityResolver identityResolver;
  private final MemoryRecordRepository memoryRecordRepository;
  private final MemoryRuntimeHotStateStore hotStateStore;
  private final SharedTaskContextService sharedTaskContextService;

  public MemoryRetrievalService(
      MemoryRuntimeProperties properties,
      MemoryIdentityResolver identityResolver,
      MemoryRecordRepository memoryRecordRepository,
      MemoryRuntimeHotStateStore hotStateStore,
      SharedTaskContextService sharedTaskContextService
  ) {
    this.properties = properties;
    this.identityResolver = identityResolver;
    this.memoryRecordRepository = memoryRecordRepository;
    this.hotStateStore = hotStateStore;
    this.sharedTaskContextService = sharedTaskContextService;
  }

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
    List<RetrievedSemanticContext> semanticCandidates = searchSemantic(identity, blank(queryText));
    return new MemoryHydration(
        buildSummary(exactRecords, semanticCandidates),
        buildSection(exactRecords, semanticCandidates),
        exactRecords,
        semanticCandidates
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
    return hotStateStore.loadWorkingMemory(identity.cacheKey())
        .flatMap(serialized -> hotStateStore.readJson(serialized, new TypeReference<List<MemoryRecord>>() {
        }))
        .orElseGet(() -> refreshExactState(identity));
  }

  public List<MemoryRecord> refreshExactState(MemoryIdentity identity) {
    List<MemoryRecord> records = memoryRecordRepository.loadExactState(identity, properties.getExactStateLimit());
    hotStateStore.storeWorkingMemory(identity.cacheKey(), records, properties.getHotStateTtl());
    return records;
  }

  public List<RetrievedSemanticContext> searchSemantic(MemoryIdentity identity, String queryText) {
    if (queryText.isBlank() || identity.projectId().isBlank()) {
      return List.of();
    }
    Map<String, Object> payloadFilter = new LinkedHashMap<>();
    payloadFilter.put("userId", blank(identity.userId()));
    payloadFilter.put("workspaceId", blank(identity.workspaceId()));
    payloadFilter.put("status", "active");
    payloadFilter.put("tombstoned", false);
    return sharedTaskContextService.searchProjectRelatedContexts(
            identity.projectId(),
            queryText,
            properties.getSemanticCandidateLimit(),
            payloadFilter
        ).stream()
        .sorted(Comparator.comparingDouble(this::compositeScore).reversed())
        .limit(properties.getSemanticCandidateLimit())
        .toList();
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

  private String buildSummary(List<MemoryRecord> exactRecords, List<RetrievedSemanticContext> semanticCandidates) {
    return "Memory pipeline retrieved "
        + exactRecords.size()
        + " exact records and "
        + semanticCandidates.size()
        + " semantic candidates.";
  }

  private String buildSection(List<MemoryRecord> exactRecords, List<RetrievedSemanticContext> semanticCandidates) {
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
                + stringValue(candidate.payload(), "kind")
                + " chunk="
                + summarize(stringValue(candidate.payload(), "chunkText"), 220))
            .collect(Collectors.joining("\n"));
    return exactSection + "\n\n" + semanticSection;
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

