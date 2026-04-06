package org.tavall.ai.app.retrieval;

import org.tavall.ai.app.persistence.qdrant.EmbeddingPurpose;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SemanticQueryPlanner {

  public SemanticQueryPlan planProjectQuery(String queryText, int limit) {
    int normalizedLimit = Math.max(1, limit);
    if (looksLikeKnowledgeQuery(queryText)) {
      return new SemanticQueryPlan(List.of(
          search(SemanticCollectionDomain.KNOWLEDGE_RULES, EmbeddingPurpose.RETRIEVAL_QUERY, normalizedLimit),
          search(SemanticCollectionDomain.CODE_REPO, EmbeddingPurpose.CODE_RETRIEVAL_QUERY, fallbackLimit(normalizedLimit)),
          search(SemanticCollectionDomain.TASK_HISTORY, EmbeddingPurpose.RETRIEVAL_QUERY, fallbackLimit(normalizedLimit)),
          search(SemanticCollectionDomain.CHAT_ARTIFACT, EmbeddingPurpose.RETRIEVAL_QUERY, tertiaryLimit(normalizedLimit))
      ));
    }
    if (looksLikeArtifactQuery(queryText)) {
      return new SemanticQueryPlan(List.of(
          search(SemanticCollectionDomain.CHAT_ARTIFACT, EmbeddingPurpose.RETRIEVAL_QUERY, normalizedLimit),
          search(SemanticCollectionDomain.TASK_HISTORY, EmbeddingPurpose.RETRIEVAL_QUERY, fallbackLimit(normalizedLimit)),
          search(SemanticCollectionDomain.CODE_REPO, EmbeddingPurpose.CODE_RETRIEVAL_QUERY, fallbackLimit(normalizedLimit)),
          search(SemanticCollectionDomain.KNOWLEDGE_RULES, EmbeddingPurpose.RETRIEVAL_QUERY, tertiaryLimit(normalizedLimit))
      ));
    }
    if (looksLikeCodeQuery(queryText)) {
      return new SemanticQueryPlan(List.of(
          search(SemanticCollectionDomain.CODE_REPO, EmbeddingPurpose.CODE_RETRIEVAL_QUERY, normalizedLimit),
          search(SemanticCollectionDomain.TASK_HISTORY, EmbeddingPurpose.RETRIEVAL_QUERY, fallbackLimit(normalizedLimit)),
          search(SemanticCollectionDomain.KNOWLEDGE_RULES, EmbeddingPurpose.RETRIEVAL_QUERY, fallbackLimit(normalizedLimit)),
          search(SemanticCollectionDomain.CHAT_ARTIFACT, EmbeddingPurpose.RETRIEVAL_QUERY, tertiaryLimit(normalizedLimit))
      ));
    }
    return new SemanticQueryPlan(List.of(
        search(SemanticCollectionDomain.TASK_HISTORY, EmbeddingPurpose.RETRIEVAL_QUERY, normalizedLimit),
        search(SemanticCollectionDomain.CODE_REPO, EmbeddingPurpose.CODE_RETRIEVAL_QUERY, fallbackLimit(normalizedLimit)),
        search(SemanticCollectionDomain.KNOWLEDGE_RULES, EmbeddingPurpose.RETRIEVAL_QUERY, fallbackLimit(normalizedLimit)),
        search(SemanticCollectionDomain.CHAT_ARTIFACT, EmbeddingPurpose.RETRIEVAL_QUERY, tertiaryLimit(normalizedLimit))
    ));
  }

  private SemanticDomainSearch search(SemanticCollectionDomain domain, EmbeddingPurpose purpose, int limit) {
    return new SemanticDomainSearch(domain, purpose, Math.max(1, limit));
  }

  private int fallbackLimit(int limit) {
    return Math.max(2, Math.min(limit, Math.max(2, limit / 2)));
  }

  private int tertiaryLimit(int limit) {
    return Math.max(1, Math.min(limit, Math.max(1, limit / 3)));
  }

  private boolean looksLikeCodeQuery(String queryText) {
    return containsAny(queryText, "code", "class", "method", "function", "implementation", "repo", "source", "file");
  }

  private boolean looksLikeKnowledgeQuery(String queryText) {
    return containsAny(queryText, "rule", "architecture", "docs", "documentation", "example", "pattern", "guideline", "convention");
  }

  private boolean looksLikeArtifactQuery(String queryText) {
    return containsAny(queryText, "chat", "log", "trace", "transcript", "debug", "screen", "screenshot", "artifact");
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

  public record SemanticQueryPlan(List<SemanticDomainSearch> searches) {
    public SemanticQueryPlan {
      searches = List.copyOf(new ArrayList<>(searches));
    }
  }

  public record SemanticDomainSearch(
      SemanticCollectionDomain domain,
      EmbeddingPurpose embeddingPurpose,
      int limit
  ) {
  }
}

