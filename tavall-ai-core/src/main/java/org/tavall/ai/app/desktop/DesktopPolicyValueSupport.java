package org.tavall.ai.app.desktop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DesktopPolicyValueSupport {

  private DesktopPolicyValueSupport() {
  }

  static String normalizeScope(String scopeKey) {
    if (scopeKey == null || scopeKey.isBlank()) {
      return "workspace-default";
    }
    return scopeKey.strip();
  }

  static String readString(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String text = String.valueOf(value).strip();
    return text.isBlank() ? fallback : text;
  }

  static boolean readBoolean(Object value, boolean fallback) {
    if (value instanceof Boolean booleanValue) {
      return booleanValue;
    }
    if (value instanceof String text) {
      if ("true".equalsIgnoreCase(text)) {
        return true;
      }
      if ("false".equalsIgnoreCase(text)) {
        return false;
      }
    }
    return fallback;
  }

  static Integer readInt(Object value, Integer fallback) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String text) {
      String normalized = text.strip();
      if (normalized.isBlank()) {
        return fallback;
      }
      try {
        return Integer.parseInt(normalized);
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  static Map<String, Object> castObjectMap(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Map<String, Object> casted = new LinkedHashMap<>();
    map.forEach((key, mapValue) -> casted.put(String.valueOf(key), mapValue));
    return casted;
  }

  static List<Map<String, Object>> castObjectList(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        Map<String, Object> casted = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> casted.put(String.valueOf(key), mapValue));
        result.add(casted);
      }
    }
    return result;
  }

  static List<String> castStringList(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (Object item : list) {
      if (item == null) {
        continue;
      }
      String text = String.valueOf(item).strip();
      if (!text.isBlank()) {
        result.add(text);
      }
    }
    return result;
  }
}

