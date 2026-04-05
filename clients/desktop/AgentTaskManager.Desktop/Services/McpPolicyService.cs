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
                HarnessPreferences = NormalizeHarnessPreferences(backend.HarnessPreferences, useDefaults: true)
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
        var servers = new Dictionary<string, bool>(StringComparer.OrdinalIgnoreCase);
        foreach (McpServerPolicyDto server in global.Servers)
        {
            servers[server.ServerName] = server.Enabled;
        }

        if (!repo.InheritGlobal)
        {
            servers.Clear();
        }

        foreach (McpServerPolicyDto server in repo.Servers)
        {
            servers[server.ServerName] = server.Enabled;
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

        List<string> enabledServers = servers.Where(item => item.Value).Select(item => item.Key).OrderBy(item => item).ToList();
        List<string> enabledTools = tools.Where(item => item.Value).Select(item => item.Key).OrderBy(item => item).ToList();
        HarnessPreferencesDto harnessPreferences = MergeHarnessPreferences(global.HarnessPreferences, repo.HarnessPreferences, repo.InheritGlobal);
        return new McpPolicyPreviewDto(
            repo.ScopeKey,
            enabledServers,
            enabledTools,
            harnessPreferences,
            $"Enabled servers: {enabledServers.Count}. Enabled tools: {enabledTools.Count}. Harness: {harnessPreferences.DiPreset}/{harnessPreferences.LanguagePreset}.");
    }

    private static McpPolicyScopeDto BuildDefaultGlobalPolicy()
        => Normalize(new McpPolicyScopeDto(
            "global",
            true,
            [new McpServerPolicyDto("agent-task-manager", true)],
            [new McpToolPolicyDto("agent-task-manager", "runHarnessToolBundle(repo-context)", true)],
            [new McpToolPresetDto("tjai-harness-clean-code", "tjAI Harness Clean Code", ["runHarnessToolBundle(language-context)", "runCleanJavaHarness"])],
            new HarnessPreferencesDto("service-loader", "java", "", true, ["checkstyle", "pmd", "error-prone"], "error", "fail"),
            DateTimeOffset.UtcNow));

    private static McpPolicyScopeDto BuildDefaultRepoPolicy(string scopeKey)
        => Normalize(new McpPolicyScopeDto(
            scopeKey,
            true,
            [],
            [],
            [],
            new HarnessPreferencesDto("", "", "", null, [], "", ""),
            DateTimeOffset.UtcNow));

    private static McpPolicyScopeDto Normalize(McpPolicyScopeDto policy)
        => policy with
        {
            ScopeKey = NormalizeScope(policy.ScopeKey),
            Servers = (policy.Servers ?? []).Where(server => !string.IsNullOrWhiteSpace(server.ServerName)).ToArray(),
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
        return new HarnessPreferencesDto(
            NormalizeText(preferences?.DiPreset, defaultDi),
            NormalizeText(preferences?.LanguagePreset, defaultLanguage),
            NormalizeText(preferences?.CustomDiDescriptor, string.Empty),
            preferences?.LintEnabled ?? defaultLintEnabled,
            NormalizeLintEngines(preferences?.LintEngines, defaultLintEngines),
            NormalizeText(preferences?.LintStrictness, defaultLintStrictness),
            NormalizeText(preferences?.LintUnsupportedRepoPolicy, defaultLintUnsupportedPolicy));
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
            NormalizeText(repo.LintUnsupportedRepoPolicy, NormalizeText(global.LintUnsupportedRepoPolicy, "fail")));
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

    private static string NormalizeText(string? value, string fallback)
        => string.IsNullOrWhiteSpace(value) ? fallback : value.Trim();

    private static string NormalizeScope(string? scopeKey)
        => string.IsNullOrWhiteSpace(scopeKey) ? "workspace-default" : scopeKey.Trim();
}
