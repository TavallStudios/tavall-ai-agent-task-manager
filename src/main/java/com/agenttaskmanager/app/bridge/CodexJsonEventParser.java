package com.agenttaskmanager.app.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CodexJsonEventParser {

  private final ObjectMapper objectMapper;

  public CodexJsonEventParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<CodexEventMessage> parseLine(String line) {
    List<CodexEventMessage> messages = new ArrayList<>();
    try {
      JsonNode root = objectMapper.readTree(line);
      String type = root.path("type").asText("");
      switch (type) {
        case "thread.started" -> messages.add(new CodexEventMessage(
            "thread-started",
            "codex-bridge",
            "Started thread " + root.path("thread_id").asText("")
        ));
        case "turn.started" -> messages.add(new CodexEventMessage(
            "turn-started",
            "codex-bridge",
            "Codex started processing the prompt."
        ));
        case "item.completed" -> {
          JsonNode item = root.path("item");
          if ("agent_message".equals(item.path("type").asText())) {
            String text = item.path("text").asText("");
            if (!text.isBlank()) {
              messages.add(new CodexEventMessage("agent-message", "codex", text));
            }
          } else {
            messages.add(new CodexEventMessage(
                "item-completed",
                "codex-bridge",
                item.toString()
            ));
          }
        }
        case "turn.completed" -> {
          JsonNode usage = root.path("usage");
          messages.add(new CodexEventMessage(
              "usage",
              "codex-bridge",
              "input=%s cached=%s output=%s".formatted(
                  usage.path("input_tokens").asText("0"),
                  usage.path("cached_input_tokens").asText("0"),
                  usage.path("output_tokens").asText("0")
              )
          ));
        }
        default -> messages.add(new CodexEventMessage("codex-event", "codex-bridge", line));
      }
    } catch (Exception ignored) {
      messages.add(new CodexEventMessage("stdout", "codex-bridge", line));
    }
    return messages;
  }
}

