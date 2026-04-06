package org.tavall.ai.app.memory;

import org.tavall.ai.app.security.AuthenticatedClientContext;
import org.tavall.ai.app.security.AuthenticatedClientContextHolder;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MemoryIdentityResolver {

  private final AuthenticatedClientContextHolder contextHolder;

  public MemoryIdentityResolver(AuthenticatedClientContextHolder contextHolder) {
    this.contextHolder = contextHolder;
  }

  public MemoryIdentity resolve(
      String projectId,
      String threadKey,
      String sessionId,
      String requestedBy,
      String requestedFrom,
      String repoPath,
      Map<String, Object> metadata
  ) {
    Optional<AuthenticatedClientContext> authenticated = contextHolder.current();
    String chatId = firstNonBlank(metadata, "chatId", "conversationKey", "threadKey");
    String effectiveProjectId = firstNonBlank(metadata, "projectKey");
    if (effectiveProjectId.isBlank()) {
      effectiveProjectId = blank(projectId);
    }
    String effectiveThreadKey = blank(threadKey);
    if (effectiveThreadKey.isBlank()) {
      effectiveThreadKey = deriveThreadKey(authenticated.orElse(null), effectiveProjectId, chatId, sessionId);
    }
    AuthenticatedClientContext context = authenticated.orElse(null);
    return new MemoryIdentity(
        context == null ? "" : blank(context.userId()),
        context == null ? "" : blank(context.workspaceId()),
        context == null ? "" : blank(context.apiKeyId()),
        effectiveProjectId,
        chatId,
        blank(sessionId),
        effectiveThreadKey,
        context == null ? blank(requestedBy) : context.requestedBy(),
        blank(requestedFrom),
        blank(repoPath)
    );
  }

  private String deriveThreadKey(
      AuthenticatedClientContext context,
      String projectId,
      String chatId,
      String sessionId
  ) {
    String prefix = context == null
        ? "mcp-http"
        : "continuity:" + blank(context.workspaceId()) + ":" + blank(context.userId());
    if (!blank(chatId).isBlank()) {
      return prefix + ":chat:" + chatId.strip();
    }
    if (!blank(projectId).isBlank()) {
      return prefix + ":project:" + projectId.strip();
    }
    return prefix + ":session:" + blank(sessionId);
  }

  private String firstNonBlank(Map<String, Object> metadata, String... keys) {
    if (metadata == null) {
      return "";
    }
    for (String key : keys) {
      Object value = metadata.get(key);
      if (value != null && !String.valueOf(value).isBlank()) {
        return String.valueOf(value).strip();
      }
    }
    return "";
  }

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }
}

