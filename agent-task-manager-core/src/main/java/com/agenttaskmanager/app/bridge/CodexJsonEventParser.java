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
          } else if (isToolCallItem(item)) {
            String signature = extractToolCallSignature(item);
            if (!signature.isBlank()) {
              messages.add(new CodexEventMessage("tool-call", "codex", signature));
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

  private boolean isToolCallItem(JsonNode item) {
    String itemType = item.path("type").asText("").toLowerCase();
    if (itemType.contains("tool")) {
      return true;
    }
    return !extractToolName(item).isBlank();
  }

  private String extractToolCallSignature(JsonNode item) {
    String toolName = extractToolName(item);
    if (toolName.isBlank()) {
      return "";
    }
    if ("runharnesstoolbundle".equals(toolName)) {
      String bundleName = extractBundleName(item);
      return bundleName.isBlank() ? "runHarnessToolBundle" : "runHarnessToolBundle(" + bundleName + ")";
    }
    if ("loadcleanjavataskcontext".equals(toolName)) {
      return "loadCleanJavaTaskContext";
    }
    if ("runcleanjavaharness".equals(toolName)) {
      return "runCleanJavaHarness";
    }
    if ("preparegitbranch".equals(toolName)) {
      return "prepareGitBranch";
    }
    if ("creategitcommit".equals(toolName)) {
      return "createGitCommit";
    }
    if ("plangitcommit".equals(toolName)) {
      return "planGitCommit";
    }
    return "";
  }

  private String extractToolName(JsonNode item) {
    String directName = normalize(item.path("name").asText(""));
    if (!directName.isBlank()) {
      return directName;
    }
    String toolName = normalize(item.path("tool_name").asText(""));
    if (!toolName.isBlank()) {
      return toolName;
    }
    String camelToolName = normalize(item.path("toolName").asText(""));
    if (!camelToolName.isBlank()) {
      return camelToolName;
    }
    JsonNode function = item.path("function");
    if (!function.isMissingNode()) {
      String functionName = normalize(function.path("name").asText(""));
      if (!functionName.isBlank()) {
        return functionName;
      }
    }
    return "";
  }

  private String extractBundleName(JsonNode item) {
    JsonNode args = item.path("arguments");
    if (args.isMissingNode() || args.isNull()) {
      args = item.path("input");
    }
    if (args.isTextual()) {
      try {
        args = objectMapper.readTree(args.asText());
      } catch (Exception ignored) {
        return "";
      }
    }
    if (args.isObject()) {
      return args.path("bundleName").asText("").strip();
    }
    JsonNode function = item.path("function");
    if (function.isObject()) {
      JsonNode functionArgs = function.path("arguments");
      if (functionArgs.isTextual()) {
        try {
          functionArgs = objectMapper.readTree(functionArgs.asText());
        } catch (Exception ignored) {
          return "";
        }
      }
      if (functionArgs.isObject()) {
        return functionArgs.path("bundleName").asText("").strip();
      }
    }
    return "";
  }

  private static String normalize(String value) {
    return value == null
        ? ""
        : value.toLowerCase().replace(" ", "").replace("_", "").replace("\"", "").strip();
  }
}
