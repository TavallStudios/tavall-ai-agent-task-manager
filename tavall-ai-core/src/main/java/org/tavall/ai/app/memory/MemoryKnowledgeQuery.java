package org.tavall.ai.app.memory;

import java.util.Map;

public record MemoryKnowledgeQuery(
    String projectId,
    String repoPath,
    String queryText,
    int limit,
    Map<String, Object> metadata
) {

  private static final int MAX_QUERY_CHARACTERS = 8_000;

  public MemoryKnowledgeQuery {
    projectId = projectId == null ? "" : projectId.strip();
    repoPath = repoPath == null ? "" : repoPath.strip();
    queryText = bound(queryText);
    limit = Math.max(1, limit);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  private static String bound(String value) {
    String normalized = value == null ? "" : value.strip();
    return normalized.length() <= MAX_QUERY_CHARACTERS
        ? normalized
        : normalized.substring(0, MAX_QUERY_CHARACTERS);
  }
}
