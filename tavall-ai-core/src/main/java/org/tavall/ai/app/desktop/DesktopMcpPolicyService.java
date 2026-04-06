package org.tavall.ai.app.desktop;

import static org.tavall.ai.app.desktop.DesktopPolicyValueSupport.castObjectList;
import static org.tavall.ai.app.desktop.DesktopPolicyValueSupport.castObjectMap;
import static org.tavall.ai.app.desktop.DesktopPolicyValueSupport.castStringList;
import static org.tavall.ai.app.desktop.DesktopPolicyValueSupport.normalizeScope;
import static org.tavall.ai.app.desktop.DesktopPolicyValueSupport.readBoolean;
import static org.tavall.ai.app.desktop.DesktopPolicyValueSupport.readString;

import org.tavall.ai.app.persistence.postgres.DesktopMcpPolicyRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DesktopMcpPolicyService {

  private final DesktopMcpPolicyRepository repository;

  public DesktopMcpPolicyService(DesktopMcpPolicyRepository repository) {
    this.repository = repository;
  }

  public Map<String, Object> loadGlobalPolicy() {
    return normalizePolicy(
        repository.loadPolicy("global").orElseGet(this::defaultGlobalPolicy),
        "global",
        true
    );
  }

  public Map<String, Object> loadRepoPolicy(String scopeKey) {
    String normalizedScope = normalizeScope(scopeKey);
    return normalizePolicy(
        repository.loadPolicy(normalizedScope).orElseGet(() -> defaultRepoPolicy(normalizedScope)),
        normalizedScope,
        true
    );
  }

  public Map<String, Object> saveGlobalPolicy(Map<String, Object> policy) {
    Map<String, Object> normalized = normalizePolicy(policy, "global", true);
    repository.upsertPolicy("global", normalized);
    return normalized;
  }

  public Map<String, Object> saveRepoPolicy(String scopeKey, Map<String, Object> policy) {
    String normalizedScope = normalizeScope(scopeKey);
    Map<String, Object> normalized = normalizePolicy(policy, normalizedScope, true);
    repository.upsertPolicy(normalizedScope, normalized);
    return normalized;
  }

  public Map<String, Object> loadMergedPreview(String scopeKey) {
    Map<String, Object> global = loadGlobalPolicy();
    Map<String, Object> repo = loadRepoPolicy(scopeKey);
    boolean inheritGlobal = readBoolean(repo.get("inheritGlobal"), true);

    Map<String, Boolean> servers = mergeServers(global, repo, inheritGlobal);
    Map<String, Boolean> tools = mergeTools(global, repo, inheritGlobal);
    List<String> enabledServers = enabledItems(servers);
    List<String> enabledTools = enabledItems(tools);
    Map<String, Object> mergedHarnessPreferences = mergeHarnessPreferences(
        castObjectMap(global.get("harnessPreferences")),
        castObjectMap(repo.get("harnessPreferences")),
        inheritGlobal
    );

    Map<String, Object> preview = new LinkedHashMap<>();
    preview.put("scopeKey", readString(repo.get("scopeKey"), normalizeScope(scopeKey)));
    preview.put("enabledServers", enabledServers);
    preview.put("enabledTools", enabledTools);
    preview.put("harnessPreferences", mergedHarnessPreferences);
    preview.put(
        "summary",
        "Enabled servers: " + enabledServers.size()
            + ". Enabled tools: " + enabledTools.size()
            + ". Harness: " + readString(mergedHarnessPreferences.get("diPreset"), DesktopHarnessPreferencePolicy.DEFAULT_DI_PRESET)
            + "/" + readString(mergedHarnessPreferences.get("languagePreset"), DesktopHarnessPreferencePolicy.DEFAULT_LANGUAGE_PRESET)
            + " lint=" + readString(mergedHarnessPreferences.get("lintStrictness"), DesktopHarnessPreferencePolicy.DEFAULT_LINT_STRICTNESS) + "."
    );
    return preview;
  }

  public DesktopHarnessPreferenceCaps loadHarnessPreferenceCaps(String scopeKey) {
    String normalizedScope = normalizeScope(scopeKey);
    Map<String, Object> global = loadGlobalPolicy();
    Map<String, Object> repo = loadRepoPolicy(normalizedScope);
    boolean inheritGlobal = readBoolean(repo.get("inheritGlobal"), true);
    Map<String, Object> mergedHarnessPreferences = mergeHarnessPreferences(
        castObjectMap(global.get("harnessPreferences")),
        castObjectMap(repo.get("harnessPreferences")),
        inheritGlobal
    );
    return DesktopHarnessPreferencePolicy.toCaps(mergedHarnessPreferences);
  }

  private Map<String, Object> normalizePolicy(Map<String, Object> policy, String scopeKey, boolean defaultInheritGlobal) {
    Map<String, Object> source = policy == null ? Map.of() : policy;
    boolean globalScope = "global".equalsIgnoreCase(normalizeScope(scopeKey));
    Map<String, Object> normalized = new LinkedHashMap<>();
    normalized.put("scopeKey", normalizeScope(readString(source.get("scopeKey"), scopeKey)));
    normalized.put("inheritGlobal", readBoolean(source.get("inheritGlobal"), defaultInheritGlobal));
    normalized.put("servers", normalizeServers(source.get("servers")));
    normalized.put("tools", normalizeTools(source.get("tools")));
    normalized.put("presets", normalizePresets(source.get("presets")));
    normalized.put("harnessPreferences", normalizeHarnessPreferences(source.get("harnessPreferences"), globalScope));
    normalized.put("updatedAt", readString(source.get("updatedAt"), OffsetDateTime.now().toString()));
    return normalized;
  }

  private Map<String, Boolean> mergeServers(Map<String, Object> global, Map<String, Object> repo, boolean inheritGlobal) {
    Map<String, Boolean> servers = new LinkedHashMap<>();
    if (inheritGlobal) {
      for (Map<String, Object> server : castObjectList(global.get("servers"))) {
        servers.put(readString(server.get("serverName"), ""), readBoolean(server.get("enabled"), true));
      }
    }
    for (Map<String, Object> server : castObjectList(repo.get("servers"))) {
      servers.put(readString(server.get("serverName"), ""), readBoolean(server.get("enabled"), true));
    }
    return servers;
  }

  private Map<String, Boolean> mergeTools(Map<String, Object> global, Map<String, Object> repo, boolean inheritGlobal) {
    Map<String, Boolean> tools = new LinkedHashMap<>();
    if (inheritGlobal) {
      for (Map<String, Object> tool : castObjectList(global.get("tools"))) {
        addToolEnabled(tools, tool);
      }
    }
    for (Map<String, Object> tool : castObjectList(repo.get("tools"))) {
      addToolEnabled(tools, tool);
    }
    return tools;
  }

  private void addToolEnabled(Map<String, Boolean> tools, Map<String, Object> tool) {
    String key = toolKey(tool);
    if (!key.isBlank()) {
      tools.put(key, readBoolean(tool.get("enabled"), true));
    }
  }

  private List<String> enabledItems(Map<String, Boolean> items) {
    return items.entrySet().stream()
        .filter(Map.Entry::getValue)
        .map(Map.Entry::getKey)
        .filter(value -> !value.isBlank())
        .sorted()
        .toList();
  }

  private List<Map<String, Object>> normalizeServers(Object value) {
    List<Map<String, Object>> normalized = new ArrayList<>();
    for (Map<String, Object> server : castObjectList(value)) {
      String name = readString(server.get("serverName"), "");
      if (name.isBlank()) {
        continue;
      }
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("serverName", name);
      item.put("enabled", readBoolean(server.get("enabled"), true));
      normalized.add(item);
    }
    return normalized;
  }

  private List<Map<String, Object>> normalizeTools(Object value) {
    List<Map<String, Object>> normalized = new ArrayList<>();
    for (Map<String, Object> tool : castObjectList(value)) {
      String serverName = readString(tool.get("serverName"), "");
      String toolName = readString(tool.get("toolName"), "");
      if (serverName.isBlank() || toolName.isBlank()) {
        continue;
      }
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("serverName", serverName);
      item.put("toolName", toolName);
      item.put("enabled", readBoolean(tool.get("enabled"), true));
      normalized.add(item);
    }
    return normalized;
  }

  private List<Map<String, Object>> normalizePresets(Object value) {
    List<Map<String, Object>> normalized = new ArrayList<>();
    for (Map<String, Object> preset : castObjectList(value)) {
      String presetKey = readString(preset.get("presetKey"), "");
      if (presetKey.isBlank()) {
        continue;
      }
      List<String> selectors = castStringList(preset.get("toolSelectors"));
      if (selectors.isEmpty()) {
        selectors = castStringList(preset.get("toolKeys"));
      }
      Set<String> distinctSelectors = new LinkedHashSet<>(selectors);
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("presetKey", presetKey);
      item.put("displayName", readString(preset.get("displayName"), presetKey));
      item.put("toolSelectors", new ArrayList<>(distinctSelectors));
      normalized.add(item);
    }
    return normalized;
  }

  private Map<String, Object> normalizeHarnessPreferences(Object value, boolean useDefaults) {
    return DesktopHarnessPreferencePolicy.normalize(castObjectMap(value), useDefaults);
  }

  private Map<String, Object> mergeHarnessPreferences(
      Map<String, Object> globalPreferences,
      Map<String, Object> repoPreferences,
      boolean inheritGlobal
  ) {
    return DesktopHarnessPreferencePolicy.merge(globalPreferences, repoPreferences, inheritGlobal);
  }

  private String toolKey(Map<String, Object> tool) {
    String serverName = readString(tool.get("serverName"), "");
    String toolName = readString(tool.get("toolName"), "");
    if (serverName.isBlank() || toolName.isBlank()) {
      return "";
    }
    return serverName + "::" + toolName;
  }

  private Map<String, Object> defaultGlobalPolicy() {
    Map<String, Object> defaultPolicy = new LinkedHashMap<>();
    defaultPolicy.put("scopeKey", "global");
    defaultPolicy.put("inheritGlobal", true);
    defaultPolicy.put("servers", List.of(Map.of("serverName", "tavall-ai", "enabled", true)));
    defaultPolicy.put("tools", List.of(Map.of(
        "serverName",
        "tavall-ai",
        "toolName",
        "runHarnessToolBundle(repo-context)",
        "enabled",
        true
    )));
    defaultPolicy.put("presets", List.of(Map.of(
        "presetKey",
        "tjai-harness-clean-code",
        "displayName",
        "tjAI Harness Clean Code",
        "toolSelectors",
        List.of("runHarnessToolBundle(language-context)", "runCleanJavaHarness")
    )));
    defaultPolicy.put("harnessPreferences", Map.of(
        "diPreset", DesktopHarnessPreferencePolicy.DEFAULT_DI_PRESET,
        "languagePreset", DesktopHarnessPreferencePolicy.DEFAULT_LANGUAGE_PRESET,
        "customDiDescriptor", "",
        "lintEnabled", DesktopHarnessPreferencePolicy.DEFAULT_LINT_ENABLED,
        "lintEngines", DesktopHarnessPreferencePolicy.DEFAULT_LINT_ENGINES,
        "lintStrictness", DesktopHarnessPreferencePolicy.DEFAULT_LINT_STRICTNESS,
        "lintUnsupportedRepoPolicy", DesktopHarnessPreferencePolicy.DEFAULT_LINT_UNSUPPORTED_REPO_POLICY,
        "internalConcurrencyCap", DesktopHarnessPreferencePolicy.DEFAULT_INTERNAL_CONCURRENCY_CAP,
        "downstreamConcurrencyCap", DesktopHarnessPreferencePolicy.DEFAULT_DOWNSTREAM_CONCURRENCY_CAP
    ));
    defaultPolicy.put("updatedAt", OffsetDateTime.now().toString());
    return defaultPolicy;
  }

  private Map<String, Object> defaultRepoPolicy(String scopeKey) {
    Map<String, Object> defaultPolicy = new LinkedHashMap<>();
    defaultPolicy.put("scopeKey", normalizeScope(scopeKey));
    defaultPolicy.put("inheritGlobal", true);
    defaultPolicy.put("servers", List.of());
    defaultPolicy.put("tools", List.of());
    defaultPolicy.put("presets", List.of());
    defaultPolicy.put("harnessPreferences", Map.of(
        "diPreset", "",
        "languagePreset", "",
        "customDiDescriptor", "",
        "lintEngines", List.of(),
        "lintStrictness", "",
        "lintUnsupportedRepoPolicy", ""
    ));
    defaultPolicy.put("updatedAt", OffsetDateTime.now().toString());
    return defaultPolicy;
  }
}


