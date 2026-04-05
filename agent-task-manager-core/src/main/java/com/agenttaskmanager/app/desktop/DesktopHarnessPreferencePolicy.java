package com.agenttaskmanager.app.desktop;

import static com.agenttaskmanager.app.desktop.DesktopPolicyValueSupport.castStringList;
import static com.agenttaskmanager.app.desktop.DesktopPolicyValueSupport.readBoolean;
import static com.agenttaskmanager.app.desktop.DesktopPolicyValueSupport.readString;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DesktopHarnessPreferencePolicy {

  static final String DEFAULT_DI_PRESET = "service-loader";
  static final String DEFAULT_LANGUAGE_PRESET = "java";
  static final boolean DEFAULT_LINT_ENABLED = true;
  static final List<String> DEFAULT_LINT_ENGINES = List.of("checkstyle", "pmd", "error-prone");
  static final String DEFAULT_LINT_STRICTNESS = "error";
  static final String DEFAULT_LINT_UNSUPPORTED_REPO_POLICY = "fail";

  private DesktopHarnessPreferencePolicy() {
  }

  static Map<String, Object> normalize(Map<String, Object> source, boolean useDefaults) {
    Map<String, Object> normalized = new LinkedHashMap<>();
    normalized.put("diPreset", readString(source.get("diPreset"), useDefaults ? DEFAULT_DI_PRESET : ""));
    normalized.put("languagePreset", readString(source.get("languagePreset"), useDefaults ? DEFAULT_LANGUAGE_PRESET : ""));
    normalized.put("customDiDescriptor", readString(source.get("customDiDescriptor"), ""));
    if (source.containsKey("lintEnabled")) {
      normalized.put("lintEnabled", readBoolean(source.get("lintEnabled"), useDefaults ? DEFAULT_LINT_ENABLED : false));
    } else {
      normalized.put("lintEnabled", useDefaults ? DEFAULT_LINT_ENABLED : null);
    }
    normalized.put("lintEngines", normalizeLintEngines(source.get("lintEngines"), useDefaults));
    normalized.put("lintStrictness", readString(source.get("lintStrictness"), useDefaults ? DEFAULT_LINT_STRICTNESS : ""));
    normalized.put(
        "lintUnsupportedRepoPolicy",
        readString(source.get("lintUnsupportedRepoPolicy"), useDefaults ? DEFAULT_LINT_UNSUPPORTED_REPO_POLICY : "")
    );
    return normalized;
  }

  static Map<String, Object> merge(
      Map<String, Object> globalPreferences,
      Map<String, Object> repoPreferences,
      boolean inheritGlobal
  ) {
    if (!inheritGlobal) {
      return normalize(repoPreferences, true);
    }
    Map<String, Object> globalNormalized = normalize(globalPreferences, true);
    Map<String, Object> repoNormalized = normalize(repoPreferences, false);
    Map<String, Object> merged = new LinkedHashMap<>();
    merged.put("diPreset", readString(repoNormalized.get("diPreset"), readString(globalNormalized.get("diPreset"), DEFAULT_DI_PRESET)));
    merged.put("languagePreset", readString(repoNormalized.get("languagePreset"), readString(globalNormalized.get("languagePreset"), DEFAULT_LANGUAGE_PRESET)));
    merged.put("customDiDescriptor", readString(repoNormalized.get("customDiDescriptor"), readString(globalNormalized.get("customDiDescriptor"), "")));
    merged.put("lintEnabled", readBoolean(repoNormalized.get("lintEnabled"), readBoolean(globalNormalized.get("lintEnabled"), DEFAULT_LINT_ENABLED)));
    merged.put(
        "lintEngines",
        normalizeLintEngines(
            castStringList(repoNormalized.get("lintEngines")).isEmpty() ? globalNormalized.get("lintEngines") : repoNormalized.get("lintEngines"),
            true
        )
    );
    merged.put("lintStrictness", readString(repoNormalized.get("lintStrictness"), readString(globalNormalized.get("lintStrictness"), DEFAULT_LINT_STRICTNESS)));
    merged.put(
        "lintUnsupportedRepoPolicy",
        readString(
            repoNormalized.get("lintUnsupportedRepoPolicy"),
            readString(globalNormalized.get("lintUnsupportedRepoPolicy"), DEFAULT_LINT_UNSUPPORTED_REPO_POLICY)
        )
    );
    return merged;
  }

  static List<String> normalizeLintEngines(Object value, boolean useDefaults) {
    List<String> source = castStringList(value).stream()
        .map(item -> item == null ? "" : item.strip().toLowerCase())
        .filter(item -> !item.isBlank())
        .distinct()
        .toList();
    if (!source.isEmpty()) {
      return source;
    }
    return useDefaults ? DEFAULT_LINT_ENGINES : List.of();
  }
}
