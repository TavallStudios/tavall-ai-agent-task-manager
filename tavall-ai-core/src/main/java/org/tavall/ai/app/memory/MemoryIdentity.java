package org.tavall.ai.app.memory;

public record MemoryIdentity(
    String userId,
    String workspaceId,
    String apiKeyId,
    String projectId,
    String chatId,
    String sessionId,
    String threadKey,
    String requestedBy,
    String requestedFrom,
    String repoPath
) {

  public String cacheKey() {
    return String.join(
        "|",
        blank(userId),
        blank(workspaceId),
        blank(projectId),
        blank(chatId),
        blank(threadKey)
    );
  }

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }
}

