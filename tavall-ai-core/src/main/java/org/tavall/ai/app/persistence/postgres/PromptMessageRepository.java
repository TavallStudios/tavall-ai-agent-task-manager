package org.tavall.ai.app.persistence.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PromptMessageRepository {

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;
  private final PromptThreadRepository promptThreadRepository;

  public PromptMessageRepository(
      JdbcClient jdbcClient,
      ObjectMapper objectMapper,
      PromptThreadRepository promptThreadRepository
  ) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
    this.promptThreadRepository = promptThreadRepository;
  }

  public void appendPromptMessage(
      String requestId,
      Long runId,
      String messageKind,
      String senderName,
      String body
  ) {
    appendPromptMessage(requestId, runId, messageKind, senderName, body, Map.of());
  }

  public void appendPromptMessage(
      String requestId,
      Long runId,
      String messageKind,
      String senderName,
      String body,
      Map<String, Object> metadata
  ) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.prompt_messages (
              request_id,
              run_id,
              message_kind,
              sender_name,
              body,
              metadata
            ) VALUES (
              :requestId,
              :runId,
              :messageKind,
              :senderName,
              :body,
              CAST(:metadata AS jsonb)
            )
            """)
        .param("requestId", requestId)
        .param("runId", runId)
        .param("messageKind", messageKind)
        .param("senderName", senderName)
        .param("body", body)
        .param("metadata", writeJson(metadata))
        .update();

    promptThreadRepository.touchThread(requestId);
  }

  private String writeJson(Map<String, Object> metadata) {
    try {
      return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to serialize prompt message metadata.", exception);
    }
  }
}

