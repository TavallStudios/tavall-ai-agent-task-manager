package com.agenttaskmanager.app.bridge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agenttaskmanager.app.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CodexJsonEventParserIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private CodexJsonEventParser parser;

  @Test
  void shouldEmitHarnessToolCallSignaturesFromToolEvents() {
    String line = """
        {"type":"item.completed","item":{"type":"tool_call","name":"runHarnessToolBundle","arguments":{"bundleName":"repo-context"}}}
        """.strip();

    List<CodexEventMessage> messages = parser.parseLine(line);
    assertTrue(messages.stream().anyMatch(message -> "tool-call".equals(message.kind())));
    assertTrue(messages.stream().anyMatch(message -> "runHarnessToolBundle(repo-context)".equals(message.body())));
  }

  @Test
  void shouldEmitGitWorkflowToolCallSignaturesFromToolEvents() {
    String line = """
        {"type":"item.completed","item":{"type":"tool_call","name":"createGitCommit","arguments":{"changeType":"Changed"}}}
        """.strip();

    List<CodexEventMessage> messages = parser.parseLine(line);
    assertTrue(messages.stream().anyMatch(message -> "createGitCommit".equals(message.body())));
  }
}
