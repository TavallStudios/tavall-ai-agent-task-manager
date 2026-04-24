namespace AgentTaskManager.Desktop.Contracts;

public sealed record McpServerPolicyDto(
    string ServerName,
    bool Enabled,
    string Mode,
    IReadOnlyDictionary<string, string>? Env);

public sealed record McpToolPolicyDto(
    string ServerName,
    string ToolName,
    bool Enabled);

public sealed record McpToolPresetDto(
    string PresetKey,
    string DisplayName,
    IReadOnlyList<string> ToolSelectors);

public sealed record HarnessPreferencesDto(
    string DiPreset,
    string LanguagePreset,
    string CustomDiDescriptor,
    bool? LintEnabled,
    IReadOnlyList<string> LintEngines,
    string LintStrictness,
    string LintUnsupportedRepoPolicy,
    int? InternalConcurrencyCap,
    int? DownstreamConcurrencyCap,
    string DownstreamMcpMode);

public sealed record McpPolicyScopeDto(
    string ScopeKey,
    bool InheritGlobal,
    IReadOnlyList<McpServerPolicyDto> Servers,
    IReadOnlyList<McpToolPolicyDto> Tools,
    IReadOnlyList<McpToolPresetDto> Presets,
    HarnessPreferencesDto HarnessPreferences,
    DateTimeOffset UpdatedAt);

public sealed record McpPolicyPreviewDto(
    string ScopeKey,
    IReadOnlyList<string> EnabledServers,
    IReadOnlyList<McpServerPolicyDto> ServerPolicies,
    IReadOnlyList<string> EnabledTools,
    HarnessPreferencesDto HarnessPreferences,
    string Summary);
