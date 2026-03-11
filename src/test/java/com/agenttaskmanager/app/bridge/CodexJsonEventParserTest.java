package com.agenttaskmanager.app.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodexJsonEventParserTest {

  private final CodexJsonEventParser parser = new CodexJsonEventParser(new ObjectMapper());

  @Test
  void shouldParseAgentMessageEvents() {
    List<CodexEventMessage> messages = parser.parseLine(
        "{\"type\":\"item.completed\",\"item\":{\"type\":\"agent_message\",\"text\":\"OK\"}}"
    );

    assertEquals(1, messages.size());
    assertEquals("agent-message", messages.getFirst().kind());
    assertEquals("OK", messages.getFirst().body());
  }

  @Test
  void shouldParseUsageEvents() {
    List<CodexEventMessage> messages = parser.parseLine(
        "{\"type\":\"turn.completed\",\"usage\":{\"input_tokens\":10,\"cached_input_tokens\":2,\"output_tokens\":5}}"
    );

    assertEquals(1, messages.size());
    assertEquals("usage", messages.getFirst().kind());
    assertTrue(messages.getFirst().body().contains("input=10"));
  }
}
