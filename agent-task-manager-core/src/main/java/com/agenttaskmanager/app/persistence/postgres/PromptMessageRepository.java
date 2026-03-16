package com.agenttaskmanager.app.persistence.postgres;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PromptMessageRepository {

  private final JdbcClient jdbcClient;
  private final PromptThreadRepository promptThreadRepository;

  public PromptMessageRepository(JdbcClient jdbcClient, PromptThreadRepository promptThreadRepository) {
    this.jdbcClient = jdbcClient;
    this.promptThreadRepository = promptThreadRepository;
  }

  public void appendPromptMessage(
      String requestId,
      Long runId,
      String messageKind,
      String senderName,
      String body
  ) {
    jdbcClient.sql("""
            INSERT INTO agent_task_manager.prompt_messages (
              request_id,
              run_id,
              message_kind,
              sender_name,
              body
            ) VALUES (
              :requestId,
              :runId,
              :messageKind,
              :senderName,
              :body
            )
            """)
        .param("requestId", requestId)
        .param("runId", runId)
        .param("messageKind", messageKind)
        .param("senderName", senderName)
        .param("body", body)
        .update();

    promptThreadRepository.touchThread(requestId);
  }
}
