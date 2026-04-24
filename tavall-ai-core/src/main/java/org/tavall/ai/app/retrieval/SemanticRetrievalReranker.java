package org.tavall.ai.app.retrieval;

import org.tavall.ai.app.model.orchestration.RetrievedSemanticContext;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SemanticRetrievalReranker {

  private final SemanticQueryPlanner semanticQueryPlanner;

  public SemanticRetrievalReranker(SemanticQueryPlanner semanticQueryPlanner) {
    this.semanticQueryPlanner = semanticQueryPlanner;
  }

  public List<RetrievedSemanticContext> rerankProjectResults(
      String queryText,
      List<RetrievedSemanticContext> contexts,
      int limit,
      Map<String, Object> payloadFilter
  ) {
    Map<SemanticCollectionDomain, Integer> domainPriority = domainPriority(queryText, limit);
    return rerank(queryText, contexts, limit, payloadFilter, domainPriority);
  }

  public List<RetrievedSemanticContext> rerankKnowledgeResults(
      String queryText,
      List<RetrievedSemanticContext> contexts,
      int limit,
      Map<String, Object> payloadFilter
  ) {
    return rerank(queryText, contexts, limit, payloadFilter, Map.of(SemanticCollectionDomain.KNOWLEDGE_RULES, 0));
  }

  private List<RetrievedSemanticContext> rerank(
      String queryText,
      List<RetrievedSemanticContext> contexts,
      int limit,
      Map<String, Object> payloadFilter,
      Map<SemanticCollectionDomain, Integer> domainPriority
  ) {
    Set<String> queryTokens = tokens(queryText);
    return contexts.stream()
        .map(context -> reranked(context, queryTokens, payloadFilter, domainPriority))
        .sorted(Comparator.comparingDouble(RetrievedSemanticContext::score).reversed()
            .thenComparing(RetrievedSemanticContext::id))
        .limit(Math.max(1, limit))
        .toList();
  }

  private RetrievedSemanticContext reranked(
      RetrievedSemanticContext context,
      Set<String> queryTokens,
      Map<String, Object> payloadFilter,
      Map<SemanticCollectionDomain, Integer> domainPriority
  ) {
    double composite = context.score();
    composite += lexicalBoost(queryTokens, context.payload()) * 0.18D;
    composite += javaSymbolBoost(queryTokens, context.payload());
    composite += filterBoost(payloadFilter, context.payload());
    composite += recencyBoost(context.payload()) * 0.08D;
    composite += domainBoost(domainPriority, context.payload()) * 0.12D;
    composite += contentTypeBoost(queryTokens, context.payload()) * 0.05D;
    return new RetrievedSemanticContext(context.id(), composite, context.payload());
  }

  private Map<SemanticCollectionDomain, Integer> domainPriority(String queryText, int limit) {
    Map<SemanticCollectionDomain, Integer> priorities = new LinkedHashMap<>();
    List<SemanticQueryPlanner.SemanticDomainSearch> searches = semanticQueryPlanner.planProjectQuery(queryText, limit).searches();
    for (int index = 0; index < searches.size(); index++) {
      priorities.putIfAbsent(searches.get(index).domain(), index);
    }
    return priorities;
  }

  private double domainBoost(Map<SemanticCollectionDomain, Integer> domainPriority, Map<String, Object> payload) {
    String domainValue = stringValue(payload, "semanticDomain");
    if (domainValue.isBlank()) {
      return 0.0D;
    }
    try {
      SemanticCollectionDomain domain = SemanticCollectionDomain.valueOf(domainValue);
      int priority = domainPriority.getOrDefault(domain, domainPriority.size());
      return Math.max(0.0D, 1.0D - (priority * 0.25D));
    } catch (IllegalArgumentException exception) {
      return 0.0D;
    }
  }

  private double contentTypeBoost(Set<String> queryTokens, Map<String, Object> payload) {
    String contentType = stringValue(payload, "contentType");
    if ("CODE".equals(contentType) && looksLikeCodeQuery(queryTokens)) {
      return 1.0D;
    }
    if ("DOCUMENTATION".equals(contentType) && looksLikeDocumentationQuery(queryTokens)) {
      return 1.0D;
    }
    return 0.1D;
  }

  private double javaSymbolBoost(Set<String> queryTokens, Map<String, Object> payload) {
    if (!Boolean.parseBoolean(stringValue(payload, "javaSymbol"))) {
      return 0.0D;
    }
    double boost = 0.22D;
    if (looksLikeCodeQuery(queryTokens)) {
      boost += 0.18D;
    }
    if (matchesAny(queryTokens, stringValue(payload, "className"), stringValue(payload, "simpleName"), stringValue(payload, "sourcePath"))) {
      boost += 0.20D;
    }
    return boost;
  }

  private double filterBoost(Map<String, Object> payloadFilter, Map<String, Object> payload) {
    if (payloadFilter == null || payloadFilter.isEmpty()) {
      return 0.0D;
    }
    double boost = 0.0D;
    for (Map.Entry<String, Object> entry : payloadFilter.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      if (String.valueOf(entry.getValue()).strip().equals(stringValue(payload, entry.getKey()))) {
        boost += 0.05D;
      }
    }
    return boost;
  }

  private double recencyBoost(Map<String, Object> payload) {
    String timestamp = firstNonBlank(
        stringValue(payload, "updatedAt"),
        stringValue(payload, "indexedAt"),
        stringValue(payload, "createdAt")
    );
    if (timestamp.isBlank()) {
      return 0.1D;
    }
    try {
      long hours = Math.max(0L, ChronoUnit.HOURS.between(OffsetDateTime.parse(timestamp), OffsetDateTime.now()));
      return 1.0D / (1.0D + (hours / 24.0D));
    } catch (RuntimeException exception) {
      return 0.1D;
    }
  }

  private double lexicalBoost(Set<String> queryTokens, Map<String, Object> payload) {
    if (queryTokens.isEmpty()) {
      return 0.0D;
    }
    List<String> haystacks = new ArrayList<>();
    haystacks.add(stringValue(payload, "title"));
    haystacks.add(stringValue(payload, "chunkText"));
    haystacks.add(stringValue(payload, "body"));
    haystacks.add(stringValue(payload, "sourcePath"));
    haystacks.add(stringValue(payload, "className"));
    haystacks.add(stringValue(payload, "simpleName"));
    int matches = 0;
    for (String token : queryTokens) {
      if (matchesToken(token, haystacks)) {
        matches++;
      }
    }
    return Math.min(1.0D, matches / (double) queryTokens.size());
  }

  private boolean matchesToken(String token, List<String> haystacks) {
    for (String haystack : haystacks) {
      if (!haystack.isBlank() && haystack.toLowerCase(Locale.ROOT).contains(token)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesAny(Set<String> queryTokens, String... values) {
    for (String value : values) {
      if (value == null || value.isBlank()) {
        continue;
      }
      String normalized = value.toLowerCase(Locale.ROOT);
      for (String token : queryTokens) {
        if (normalized.contains(token)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean looksLikeCodeQuery(Set<String> queryTokens) {
    return queryTokens.stream().anyMatch(token -> token.equals("class")
        || token.equals("method")
        || token.equals("field")
        || token.equals("function")
        || token.equals("java")
        || token.equals("signature")
        || token.equals("contract")
        || token.equals("code"));
  }

  private boolean looksLikeDocumentationQuery(Set<String> queryTokens) {
    return queryTokens.stream().anyMatch(token -> token.equals("rule")
        || token.equals("docs")
        || token.equals("documentation")
        || token.equals("architecture")
        || token.equals("guide"));
  }

  private Set<String> tokens(String queryText) {
    if (queryText == null || queryText.isBlank()) {
      return Set.of();
    }
    Set<String> tokens = new LinkedHashSet<>();
    for (String token : queryText.toLowerCase(Locale.ROOT).split("[^a-z0-9_.$]+")) {
      if (token != null && token.length() >= 2) {
        tokens.add(token);
      }
    }
    return Set.copyOf(tokens);
  }

  private String stringValue(Map<String, Object> payload, String key) {
    Object value = payload == null ? null : payload.get(key);
    return value == null ? "" : String.valueOf(value).strip();
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }
}

