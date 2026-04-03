package com.agenttaskmanager.app.service.session;

import com.agenttaskmanager.app.model.session.CodexSessionApiModels.PatchFileChange;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class InMemorySessionProjectionSupport {

  private InMemorySessionProjectionSupport() {
  }

  static int findTurnIndex(InMemoryCodexSessionState session, String turnId) {
    for (int index = 0; index < session.turns.size(); index++) {
      if (turnId.equals(session.turns.get(index).turnId())) {
        return index;
      }
    }
    return -1;
  }

  static int findVerifierIndex(InMemoryCodexSessionState session, String turnId) {
    for (int index = 0; index < session.verifierResults.size(); index++) {
      if (turnId.equals(session.verifierResults.get(index).turnId())) {
        return index;
      }
    }
    return -1;
  }

  static List<PatchFileChange> parseChangedFiles(Object value) {
    if (!(value instanceof List<?> items)) {
      return List.of();
    }
    List<PatchFileChange> changes = new ArrayList<>();
    for (Object item : items) {
      if (item instanceof Map<?, ?> map) {
        Integer addedLines = intValue(map.get("addedLines"));
        Integer removedLines = intValue(map.get("removedLines"));
        changes.add(new PatchFileChange(
            stringValue(map, "path", ""),
            stringValue(map, "changeType", "update"),
            addedLines == null ? 0 : addedLines,
            removedLines == null ? 0 : removedLines
        ));
      }
    }
    return changes;
  }

  static Map<?, ?> mapValue(Object value) {
    return value instanceof Map<?, ?> map ? map : Map.of();
  }

  static Integer intValue(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String string && !string.isBlank()) {
      try {
        return Integer.parseInt(string.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  static String extractContentText(Map<?, ?> item) {
    Object contentValue = item.get("content");
    if (!(contentValue instanceof List<?> contentItems)) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    for (Object contentItem : contentItems) {
      if (contentItem instanceof Map<?, ?> contentMap) {
        String text = stringValue(contentMap, "text", "");
        if (!text.isBlank()) {
          if (builder.length() > 0) {
            builder.append('\n');
          }
          builder.append(text);
        }
      }
    }
    return builder.toString();
  }

  static String summarizeOutput(Map<?, ?> item, String content, boolean approved) {
    if (!content.isBlank()) {
      return content.length() > 120 ? content.substring(0, 117) + "..." : content;
    }
    String phase = stringValue(item, "phase", approved ? "approved output" : "candidate output");
    return phase.replace('_', ' ');
  }

  static String stringValue(Map<?, ?> map, String key, String defaultValue) {
    Object value = map.get(key);
    if (value instanceof String string && !string.isBlank()) {
      return string;
    }
    return defaultValue;
  }

  static String nonBlank(String primary, String fallback) {
    return primary == null || primary.isBlank() ? fallback : primary;
  }

  static String nonBlank(String first, String second, String fallback) {
    return nonBlank(nonBlank(first, second), fallback);
  }

  static String nonBlank(String first, String second, String third, String fallback) {
    return nonBlank(nonBlank(first, second, third), fallback);
  }

  static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
