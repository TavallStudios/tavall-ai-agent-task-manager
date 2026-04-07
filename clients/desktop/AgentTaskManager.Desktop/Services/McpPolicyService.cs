using AgentTaskManager.Desktop.Contracts;
using System.Net.Http.Json;
using System.Text.Json;

namespace AgentTaskManager.Desktop.Services;

public sealed class McpPolicyService : IMcpPolicyService
{
    private readonly IBackendAuthService _backendAuthService;
    private readonly IDesktopConnectionSettingsService _connectionSettingsService;

    public McpPolicyService(
        IBackendAuthService backendAuthService,
        IDesktopConnectionSettingsService connectionSettingsService)
    {
        _backendAuthService = backendAuthService;
        _connectionSettingsService = connectionSettingsService;
    }

    public async Task<McpPolicyScopeDto> LoadGlobalPolicyAsync(CancellationToken cancellationToken)
    {
        McpPolicyScopeDto? backend = await TryLoadBackendAsync("api/desktop/mcp-policy/global", cancellationToken);
        if (backend is not null)
        {
            await SaveFileAsync(DesktopStoragePaths.McpPolicyGlobalFile, backend, cancellationToken);
            return backend;
        }

        return await LoadFileAsync(
            DesktopStoragePaths.McpPolicyGlobalFile,
            BuildDefaultGlobalPolicy,
            cancellationToken);
    }

    public async Task<McpPolicyScopeDto> LoadRepoPolicyAsync(string scopeKey, CancellationToken cancellationToken)
    {
        string normalizedScope = NormalizeScope(scopeKey);
        McpPolicyScopeDto? backend = await TryLoadBackendAsync(
            $"api/desktop/mcp-policy/repos/{Uri.EscapeDataString(normalizedScope)}",
            cancellationToken);
        if (backend is not null)
        {
            await SaveFileAsync(DesktopStoragePaths.GetMcpPolicyRepoFile(normalizedScope), backend, cancellationToken);
            return backend;
        }

        return await LoadFileAsync(
            DesktopStoragePaths.GetMcpPolicyRepoFile(normalizedScope),
            () => BuildDefaultRepoPolicy(normalizedScope),
            cancellationToken);
    }

    public async Task SaveGlobalPolicyAsync(McpPolicyScopeDto policy, CancellationToken cancellationToken)
    {
        McpPolicyScopeDto normalized = Normalize(policy with { ScopeKey = "global" });
        await SaveFileAsync(DesktopStoragePaths.McpPolicyGlobalFile, normalized, cancellationToken);
        await TrySaveBackendAsync("api/desktop/mcp-policy/global", normalized, cancellationToken);
    }

    public async Task SaveRepoPolicyAsync(McpPolicyScopeDto policy, CancellationToken cancellationToken)
    {
        string normalizedScope = NormalizeScope(policy.ScopeKey);
        McpPolicyScopeDto normalized = Normalize(policy with { ScopeKey = normalizedScope });
        await SaveFileAsync(DesktopStoragePaths.GetMcpPolicyRepoFile(normalizedScope), normalized, cancellationToken);
        await TrySaveBackendAsync(
            $"api/desktop/mcp-policy/repos/{Uri.EscapeDataString(normalizedScope)}",
            normalized,
            cancellationToken);
    }

    public async Task<McpPolicyPreviewDto> LoadMergedPreviewAsync(string scopeKey, CancellationToken cancellationToken)
    {
        string normalizedScope = NormalizeScope(scopeKey);
        McpPolicyPreviewDto? backend = await TryLoadPreviewBackendAsync(normalizedScope, cancellationToken);
        if (backend is not null)
        {
            return backend with
            {
                HarnessPreferences = NormalizeHarnessPreferences(backend.HarnessPreferences, useDefaults: true),
                ServerPolicies = NormalizeServerPolicies(backend.ServerPolicies, useDefaults: true)
            };
        }

        McpPolicyScopeDto global = await LoadGlobalPolicyAsync(cancellationToken);
        McpPolicyScopeDto repo = await LoadRepoPolicyAsync(normalizedScope, cancellationToken);
        return Merge(global, repo);
    }

    private async Task<McpPolicyScopeDto?> TryLoadBackendAsync(string path, CancellationToken cancellationToken)
    {
        try
        {
            await _connectionSettingsService.EnsureBackendTransportAsync(cancellationToken);
            using var client = new HttpClient { BaseAddress = _backendAuthService.GetBackendBaseUri() };
            using var request = new HttpRequestMessage(HttpMethod.Get, path);
            _backendAuthService.ApplyAuthentication(request);
            using HttpResponseMessage response = await client.SendAsync(request, cancellationToken);
            if (!response.IsSuccessStatusCode)
            {
                return null;
            }

            McpPolicyScopeDto? value = await response.Content.ReadFromJsonAsync<McpPolicyScopeDto>(DesktopJson.Default, cancellationToken);
            return value is null ? null : Normalize(value);
        }
        catch
        {
            return null;
        }
    }

    private async Task<McpPolicyPreviewDto?> TryLoadPreviewBackendAsync(string scopeKey, CancellationToken cancellationToken)
    {
        try
        {
            await _connectionSettingsService.EnsureBackendTransportAsync(cancellationToken);
            using var client = new HttpClient { BaseAddress = _backendAuthService.GetBackendBaseUri() };
            using var request = new HttpRequestMessage(
                HttpMethod.Get,
                $"api/desktop/mcp-policy/preview?scopeKey={Uri.EscapeDataString(scopeKey)}");
            _backendAuthService.ApplyAuthentication(request);
            using HttpResponseMessage response = await client.SendAsync(request, cancellationToken);
            if (!response.IsSuccessStatusCode)
            {
                return null;
            }

            return await response.Content.ReadFromJsonAsync<McpPolicyPreviewDto>(DesktopJson.Default, cancellationToken);
        }
        catch
        {
            return null;
        }
    }

    private async Task TrySaveBackendAsync(string path, McpPolicyScopeDto policy, CancellationToken cancellationToken)
    {
        try
        {
            await _connectionSettingsService.EnsureBackendTransportAsync(cancellationToken);
            using var client = new HttpClient { BaseAddress = _backendAuthService.GetBackendBaseUri() };
            using var request = new HttpRequestMessage(HttpMethod.Put, path)
            {
                Content = JsonContent.Create(policy, options: DesktopJson.Default)
            };
            _backendAuthService.ApplyAuthentication(request);
            _ = await client.SendAsync(request, cancellationToken);
        }
        catch
        {
            // Local persistence remains authoritative fallback.
        }
    }

    private static async Task<McpPolicyScopeDto> LoadFileAsync(
        string path,
        Func<McpPolicyScopeDto> defaultFactory,
        CancellationToken cancellationToken)
    {
        DesktopStoragePaths.EnsureCreated();
        if (!File.Exists(path))
        {
            McpPolicyScopeDto created = Normalize(defaultFactory());
            await SaveFileAsync(path, created, cancellationToken);
            return created;
        }

        string json = await File.ReadAllTextAsync(path, cancellationToken);
        McpPolicyScopeDto? value = JsonSerializer.Deserialize<McpPolicyScopeDto>(json, DesktopJson.Default);
        return Normalize(value ?? defaultFactory());
    }

    private static async Task SaveFileAsync(string path, McpPolicyScopeDto policy, CancellationToken cancellationToken)
    {
        DesktopStoragePaths.EnsureCreated();
        string json = JsonSerializer.Serialize(policy, DesktopJson.Default);
        await File.WriteAllTextAsync(path, json, cancellationToken);
    }

    private static McpPolicyPreviewDto Merge(McpPolicyScopeDto global, McpPolicyScopeDto repo)
    {
        var servers = new Dictionary<string, McpServerPolicyDto>(StringComparer.OrdinalIgnoreCase);
        foreach (McpServerPolicyDto server in global.Servers)
        {
            servers[server.ServerName] = NormalizeServerPolicy(server, useDefaults: true);
        }

        if (!repo.InheritGlobal)
        {
            servers.Clear();
        }

        foreach (McpServerPolicyDto server in repo.Servers)
        {
            McpServerPolicyDto normalized = NormalizeServerPolicy(server, useDefaults: false);
            servers[server.ServerName] = servers.TryGetValue(server.ServerName, out McpServerPolicyDto? existing)
                ? MergeServerPolicy(existing, normalized)
                : normalized;
        }

        var tools = new Dictionary<string, bool>(StringComparer.OrdinalIgnoreCase);
        foreach (McpToolPolicyDto tool in global.Tools)
        {
            tools[$"{tool.ServerName}::{tool.ToolName}"] = tool.Enabled;
        }

        if (!repo.InheritGlobal)
        {
            tools.Clear();
        }

        foreach (McpToolPolicyDto tool in repo.Tools)
        {
            tools[$"{tool.ServerName}::{tool.ToolName}"] = tool.Enabled;
        }

        List<McpServerPolicyDto> mergedServers = servers.Values.OrderBy(server => server.ServerName).ToList();
        List<string> enabledServers = mergedServers.Where(item => item.Enabled).Select(item => item.ServerName).ToList();
        List<string> enabledTools = tools.Where(item => item.Value).Select(item => item.Key).OrderBy(item => item).ToList();
        HarnessPreferencesDto harnessPreferences = MergeHarnessPreferences(global.HarnessPreferences, repo.HarnessPreferences, repo.InheritGlobal);
        return new McpPolicyPreviewDto(
            repo.ScopeKey,
            enabledServers,
            mergedServers,
            enabledTools,
            harnessPreferences,
            $"Enabled servers: {enabledServers.Count}. Enabled tools: {enabledTools.Count}. Harness: {harnessPreferences.DiPreset}/{harnessPreferences.LanguagePreset}.");
    }

    private static McpPolicyScopeDto BuildDefaultGlobalPolicy()
        => Normalize(new McpPolicyScopeDto(
            "global",
            true,
            [new McpServerPolicyDto("tavall-ai", true, "local-only", null)],
            [new McpToolPolicyDto("tavall-ai", "runHarnessToolBundle(repo-context)", true)],
            [new McpToolPresetDto("tjai-harness-clean-code", "tjAI Harness Clean Code", ["runHarnessToolBundle(language-context)", "runCleanJavaHarness"])],
            new HarnessPreferencesDto("service-loader", "java", "", true, ["checkstyle", "pmd", "error-prone"], "error", "fail", 0, 0, "local-only"),
            DateTimeOffset.UtcNow));

    private static McpPolicyScopeDto BuildDefaultRepoPolicy(string scopeKey)
        => Normalize(new McpPolicyScopeDto(
            scopeKey,
            true,
            [],
            [],
            [],
            new HarnessPreferencesDto("", "", "", null, [], "", "", null, null, ""),
            DateTimeOffset.UtcNow));

    private static McpPolicyScopeDto Normalize(McpPolicyScopeDto policy)
        => policy with
        {
            ScopeKey = NormalizeScope(policy.ScopeKey),
            Servers = NormalizeServerPolicies(
                policy.Servers,
                NormalizeScope(policy.ScopeKey).Equals("global", StringComparison.OrdinalIgnoreCase)),
            Tools = (policy.Tools ?? [])
                .Where(tool => !string.IsNullOrWhiteSpace(tool.ServerName) && !string.IsNullOrWhiteSpace(tool.ToolName))
                .ToArray(),
            Presets = (policy.Presets ?? [])
                .Where(preset => !string.IsNullOrWhiteSpace(preset.PresetKey))
                .Select(preset => preset with
                {
                    ToolSelectors = (preset.ToolSelectors ?? []).Where(selector => !string.IsNullOrWhiteSpace(selector)).ToArray()
                })
                .ToArray(),
            HarnessPreferences = NormalizeHarnessPreferences(
                policy.HarnessPreferences,
                NormalizeScope(policy.ScopeKey).Equals("global", StringComparison.OrdinalIgnoreCase)),
            UpdatedAt = policy.UpdatedAt == default ? DateTimeOffset.UtcNow : policy.UpdatedAt
        };

    private static HarnessPreferencesDto NormalizeHarnessPreferences(HarnessPreferencesDto? preferences, bool useDefaults)
    {
        string defaultDi = useDefaults ? "service-loader" : string.Empty;
        string defaultLanguage = useDefaults ? "java" : string.Empty;
        bool defaultLintEnabled = useDefaults;
        string defaultLintStrictness = useDefaults ? "error" : string.Empty;
        string defaultLintUnsupportedPolicy = useDefaults ? "fail" : string.Empty;
        IReadOnlyList<string> defaultLintEngines = useDefaults ? ["checkstyle", "pmd", "error-prone"] : [];
        int? defaultInternalCap = useDefaults ? 0 : null;
        int? defaultDownstreamCap = useDefaults ? 0 : null;
        string defaultDownstreamMode = useDefaults ? "local-only" : string.Empty;
        return new HarnessPreferencesDto(
            NormalizeText(preferences?.DiPreset, defaultDi),
            NormalizeText(preferences?.LanguagePreset, defaultLanguage),
            NormalizeText(preferences?.CustomDiDescriptor, string.Empty),
            preferences?.LintEnabled ?? defaultLintEnabled,
            NormalizeLintEngines(preferences?.LintEngines, defaultLintEngines),
            NormalizeText(preferences?.LintStrictness, defaultLintStrictness),
            NormalizeText(preferences?.LintUnsupportedRepoPolicy, defaultLintUnsupportedPolicy),
            NormalizeCap(preferences?.InternalConcurrencyCap, defaultInternalCap),
            NormalizeCap(preferences?.DownstreamConcurrencyCap, defaultDownstreamCap),
            NormalizeText(preferences?.DownstreamMcpMode, defaultDownstreamMode));
    }

    private static HarnessPreferencesDto MergeHarnessPreferences(
        HarnessPreferencesDto global,
        HarnessPreferencesDto repo,
        bool inheritGlobal)
    {
        if (!inheritGlobal)
        {
            return NormalizeHarnessPreferences(repo, useDefaults: true);
        }

        return new HarnessPreferencesDto(
            NormalizeText(repo.DiPreset, NormalizeText(global.DiPreset, "service-loader")),
            NormalizeText(repo.LanguagePreset, NormalizeText(global.LanguagePreset, "java")),
            NormalizeText(repo.CustomDiDescriptor, NormalizeText(global.CustomDiDescriptor, string.Empty)),
            repo.LintEnabled ?? global.LintEnabled ?? true,
            NormalizeLintEngines(repo.LintEngines, NormalizeLintEngines(global.LintEngines, ["checkstyle", "pmd", "error-prone"])),
            NormalizeText(repo.LintStrictness, NormalizeText(global.LintStrictness, "error")),
            NormalizeText(repo.LintUnsupportedRepoPolicy, NormalizeText(global.LintUnsupportedRepoPolicy, "fail")),
            NormalizeCap(repo.InternalConcurrencyCap, NormalizeCap(global.InternalConcurrencyCap, 0)),
            NormalizeCap(repo.DownstreamConcurrencyCap, NormalizeCap(global.DownstreamConcurrencyCap, 0)),
            NormalizeText(repo.DownstreamMcpMode, NormalizeText(global.DownstreamMcpMode, "local-only")));
    }

    private static IReadOnlyList<string> NormalizeLintEngines(IReadOnlyList<string>? values, IReadOnlyList<string> fallback)
    {
        IReadOnlyList<string> normalized = (values ?? [])
            .Where(value => !string.IsNullOrWhiteSpace(value))
            .Select(value => value.Trim().ToLowerInvariant())
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToArray();
        return normalized.Count > 0 ? normalized : fallback;
    }

    private static int? NormalizeCap(int? value, int? fallback)
    {
        if (value is null)
        {
            return fallback;
        }

        return Math.Max(0, value.Value);
    }

    private static string NormalizeText(string? value, string fallback)
        => string.IsNullOrWhiteSpace(value) ? fallback : value.Trim();

    private static string NormalizeScope(string? scopeKey)
        => string.IsNullOrWhiteSpace(scopeKey) ? "workspace-default" : scopeKey.Trim();

    private static IReadOnlyList<McpServerPolicyDto> NormalizeServerPolicies(
        IReadOnlyList<McpServerPolicyDto>? servers,
        bool useDefaults)
        => (servers ?? Array.Empty<McpServerPolicyDto>())
            .Where(server => !string.IsNullOrWhiteSpace(server.ServerName))
            .Select(server => NormalizeServerPolicy(server, useDefaults))
            .ToArray();

    private static McpServerPolicyDto NormalizeServerPolicy(McpServerPolicyDto server, bool useDefaults)
    {
        string normalizedMode = NormalizeServerMode(server.Mode, server.ServerName, useDefaults);
        IReadOnlyDictionary<string, string>? normalizedEnv = NormalizeEnv(server.Env);
        return server with
        {
            Mode = normalizedMode,
            Env = normalizedEnv
        };
    }

    private static McpServerPolicyDto MergeServerPolicy(McpServerPolicyDto global, McpServerPolicyDto repo)
    {
        string mode = string.IsNullOrWhiteSpace(repo.Mode) ? global.Mode : repo.Mode;
        IReadOnlyDictionary<string, string>? env = repo.Env is { Count: > 0 } ? repo.Env : global.Env;
        return repo with
        {
            Mode = mode,
            Env = env
        };
    }

    private static string NormalizeServerMode(string? mode, string serverName, bool useDefaults)
    {
        if (string.IsNullOrWhiteSpace(mode))
        {
            return useDefaults ? DefaultServerMode(serverName) : string.Empty;
        }

        string normalized = mode.Trim().ToLowerInvariant();
        return normalized switch
        {
            "local-only" or "local" or "local-first" => "local-only",
            "remote-only" or "remote" or "remote-first" => "remote-only",
            "local-then-remote" or "local-remote" or "try-local-remote" => "local-then-remote",
            "remote-then-local" or "remote-local" or "try-remote-local" => "remote-then-local",
            _ => useDefaults ? DefaultServerMode(serverName) : string.Empty
        };
    }

    private static string DefaultServerMode(string serverName)
    {
        if (string.IsNullOrWhiteSpace(serverName))
        {
            return "local-only";
        }

        string normalized = serverName.Trim().ToLowerInvariant();
        if (normalized.StartsWith("qdrant")
            || normalized.StartsWith("postgres")
            || normalized.StartsWith("mongodb")
            || normalized.StartsWith("redis")
            || normalized.StartsWith("elasticsearch")
            || normalized.StartsWith("prometheus")
            || normalized.StartsWith("loki"))
        {
            return "remote-only";
        }

        return "local-only";
    }

    private static IReadOnlyDictionary<string, string>? NormalizeEnv(IReadOnlyDictionary<string, string>? env)
    {
        if (env == null || env.Count == 0)
        {
            return null;
        }

        var normalized = env
            .Where(entry => !string.IsNullOrWhiteSpace(entry.Key) && !string.IsNullOrWhiteSpace(entry.Value))
            .ToDictionary(entry => entry.Key.Trim(), entry => entry.Value.Trim(), StringComparer.OrdinalIgnoreCase);
        return normalized.Count == 0 ? null : normalized;
    }
}

