package org.tavall.ai.app.persistence.postgres;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PromptInteractionRepository {

  private static final String MCP_HTTP_TARGET = "mcp-http";

  private final JdbcClient jdbcClient;
  private final PromptThreadRepository promptThreadRepository;

  public PromptInteractionRepository(
      JdbcClient jdbcClient,
      PromptThreadRepository promptThreadRepository
  ) {
    this.jdbcClient = jdbcClient;
    this.promptThreadRepository = promptThreadRepository;
  }

  public String startInteraction(
      String projectKey,
      String repoPath,
      String threadKey,
      String interactionType,
      String promptText,
      String requestedBy,
      String requestedFrom,
      String sessionId
  ) {
    String requestId = "mcp_" + UUID.randomUUID();
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.prompt_requests (
              request_id,
              project_key,
              repo_path,
              bridge_target,
              thread_key,
              requested_by,
              requested_from,
              target_agent_id,
              execution_mode,
              status,
              prompt_text,
              latest_summary
            ) VALUES (
              :requestId,
              :projectKey,
              :repoPath,
              :bridgeTarget,
              :threadKey,
              :requestedBy,
              NULLIF(:requestedFrom, ''),
              NULLIF(:sessionId, ''),
              :executionMode,
              'running',
              :promptText,
              'Accepted via MCP HTTP'
            )
            """)
        .param("requestId", requestId)
        .param("projectKey", blank(projectKey))
        .param("repoPath", blank(repoPath))
        .param("bridgeTarget", MCP_HTTP_TARGET)
        .param("threadKey", threadKey)
        .param("requestedBy", blank(requestedBy, "mcp-client"))
        .param("requestedFrom", blank(requestedFrom))
        .param("sessionId", blank(sessionId))
        .param("executionMode", interactionType)
        .param("promptText", promptText)
        .update();
    promptThreadRepository.ensurePromptThread(
        threadKey,
        blank(projectKey),
        blank(repoPath),
        MCP_HTTP_TARGET,
        requestId
    );
    if (sessionId != null && !sessionId.isBlank()) {
      promptThreadRepository.recordSession(threadKey, sessionId);
    }
    return requestId;
  }

  public void completeInteraction(String requestId, String summary) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.prompt_requests
            SET status = 'completed',
                latest_summary = :summary,
                completed_at = now(),
                updated_at = now()
            WHERE request_id = :requestId
            """)
        .param("requestId", requestId)
        .param("summary", summary)
        .update();
  }

  public void failInteraction(String requestId, String summary) {
    jdbcClient.sql("""
            UPDATE agent_task_manager.prompt_requests
            SET status = 'failed',
                latest_summary = :summary,
                completed_at = now(),
                updated_at = now()
            WHERE request_id = :requestId
            """)
        .param("requestId", requestId)
        .param("summary", summary)
        .update();
  }

  public static String mcpHttpTarget() {
    return MCP_HTTP_TARGET;
  }

  private String blank(String value) {
    return value == null ? "" : value.strip();
  }

  private String blank(String value, String fallback) {
    String normalized = blank(value);
    return normalized.isBlank() ? fallback : normalized;
  }
}

