using AgentTaskManager.Desktop.Contracts;
using AgentTaskManager.Desktop.Services;
using CommunityToolkit.Mvvm.ComponentModel;
using System.Collections.ObjectModel;
using System.Linq;
using System.Text.Json;

namespace AgentTaskManager.Desktop.ViewModels;

public sealed class McpPolicyViewModel : ObservableObject
{
    private readonly IMcpPolicyService _policyService;
    private string _repoScopeKey = "workspace-default";
    private string _globalPolicyJson = string.Empty;
    private string _repoPolicyJson = string.Empty;
    private string _mergedPreviewJson = string.Empty;
    private string _globalDiPreset = "service-loader";
    private string _globalLanguagePreset = "java";
    private string _globalCustomDiDescriptor = string.Empty;
    private string _globalInternalConcurrencyCap = "0";
    private string _globalDownstreamConcurrencyCap = "0";
    private string _globalDownstreamMcpMode = "local-only";
    private string _repoDiPreset = string.Empty;
    private string _repoLanguagePreset = string.Empty;
    private string _repoCustomDiDescriptor = string.Empty;
    private string _repoInternalConcurrencyCap = string.Empty;
    private string _repoDownstreamConcurrencyCap = string.Empty;
    private string _repoDownstreamMcpMode = string.Empty;
    private string _effectiveDiPreset = "service-loader";
    private string _effectiveLanguagePreset = "java";
    private string _effectiveCustomDiDescriptor = string.Empty;
    private string _effectiveInternalConcurrencyCap = "0";
    private string _effectiveDownstreamConcurrencyCap = "0";
    private string _effectiveDownstreamMcpMode = "local-only";
    private readonly ObservableCollection<McpServerPolicyEditor> _globalServerPolicies = new();
    private readonly ObservableCollection<McpServerPolicyEditor> _repoServerPolicies = new();
    private McpServerPolicyEditor? _selectedGlobalServerPolicy;
    private McpServerPolicyEditor? _selectedRepoServerPolicy;
    private string _newGlobalServerName = string.Empty;
    private string _newRepoServerName = string.Empty;
    private string _statusMessage = "MCP policy settings idle.";
    private bool _isBusy;

    public McpPolicyViewModel(IMcpPolicyService policyService)
    {
        _policyService = policyService;
    }

    public IReadOnlyList<string> DiPresetOptions { get; } =
    [
        "service-loader",
        "mcrspeedrun-annotation-di",
        "guice",
        "spring-framework",
        "manual-factory"
    ];

    public IReadOnlyList<string> LanguagePresetOptions { get; } =
    [
        "java",
        "kotlin",
        "csharp",
        "typescript",
        "python",
        "go"
    ];

    public IReadOnlyList<string> DownstreamMcpModeOptions { get; } =
    [
        "local-only",
        "local-then-remote",
        "remote-only",
        "remote-then-local"
    ];

    public IReadOnlyList<string> ServerModeOptions => DownstreamMcpModeOptions;

    public string RepoScopeKey
    {
        get => _repoScopeKey;
        set => SetProperty(ref _repoScopeKey, value);
    }

    public string GlobalPolicyJson
    {
        get => _globalPolicyJson;
        set => SetProperty(ref _globalPolicyJson, value);
    }

    public string RepoPolicyJson
    {
        get => _repoPolicyJson;
        set => SetProperty(ref _repoPolicyJson, value);
    }

    public string MergedPreviewJson
    {
        get => _mergedPreviewJson;
        set => SetProperty(ref _mergedPreviewJson, value);
    }

    public string GlobalDiPreset
    {
        get => _globalDiPreset;
        set => SetProperty(ref _globalDiPreset, value);
    }

    public string GlobalLanguagePreset
    {
        get => _globalLanguagePreset;
        set => SetProperty(ref _globalLanguagePreset, value);
    }

    public string GlobalCustomDiDescriptor
    {
        get => _globalCustomDiDescriptor;
        set => SetProperty(ref _globalCustomDiDescriptor, value);
    }

    public string GlobalInternalConcurrencyCap
    {
        get => _globalInternalConcurrencyCap;
        set => SetProperty(ref _globalInternalConcurrencyCap, value);
    }

    public string GlobalDownstreamConcurrencyCap
    {
        get => _globalDownstreamConcurrencyCap;
        set => SetProperty(ref _globalDownstreamConcurrencyCap, value);
    }

    public string GlobalDownstreamMcpMode
    {
        get => _globalDownstreamMcpMode;
        set => SetProperty(ref _globalDownstreamMcpMode, value);
    }

    public string RepoDiPreset
    {
        get => _repoDiPreset;
        set => SetProperty(ref _repoDiPreset, value);
    }

    public string RepoLanguagePreset
    {
        get => _repoLanguagePreset;
        set => SetProperty(ref _repoLanguagePreset, value);
    }

    public string RepoCustomDiDescriptor
    {
        get => _repoCustomDiDescriptor;
        set => SetProperty(ref _repoCustomDiDescriptor, value);
    }

    public string RepoInternalConcurrencyCap
    {
        get => _repoInternalConcurrencyCap;
        set => SetProperty(ref _repoInternalConcurrencyCap, value);
    }

    public string RepoDownstreamConcurrencyCap
    {
        get => _repoDownstreamConcurrencyCap;
        set => SetProperty(ref _repoDownstreamConcurrencyCap, value);
    }

    public string RepoDownstreamMcpMode
    {
        get => _repoDownstreamMcpMode;
        set => SetProperty(ref _repoDownstreamMcpMode, value);
    }

    public string EffectiveDiPreset
    {
        get => _effectiveDiPreset;
        set => SetProperty(ref _effectiveDiPreset, value);
    }

    public string EffectiveLanguagePreset
    {
        get => _effectiveLanguagePreset;
        set => SetProperty(ref _effectiveLanguagePreset, value);
    }

    public string EffectiveCustomDiDescriptor
    {
        get => _effectiveCustomDiDescriptor;
        set => SetProperty(ref _effectiveCustomDiDescriptor, value);
    }

    public string EffectiveInternalConcurrencyCap
    {
        get => _effectiveInternalConcurrencyCap;
        set => SetProperty(ref _effectiveInternalConcurrencyCap, value);
    }

    public string EffectiveDownstreamConcurrencyCap
    {
        get => _effectiveDownstreamConcurrencyCap;
        set => SetProperty(ref _effectiveDownstreamConcurrencyCap, value);
    }

    public string EffectiveDownstreamMcpMode
    {
        get => _effectiveDownstreamMcpMode;
        set => SetProperty(ref _effectiveDownstreamMcpMode, value);
    }

    public ObservableCollection<McpServerPolicyEditor> GlobalServerPolicies => _globalServerPolicies;

    public ObservableCollection<McpServerPolicyEditor> RepoServerPolicies => _repoServerPolicies;

    public McpServerPolicyEditor? SelectedGlobalServerPolicy
    {
        get => _selectedGlobalServerPolicy;
        set => SetProperty(ref _selectedGlobalServerPolicy, value);
    }

    public McpServerPolicyEditor? SelectedRepoServerPolicy
    {
        get => _selectedRepoServerPolicy;
        set => SetProperty(ref _selectedRepoServerPolicy, value);
    }

    public string NewGlobalServerName
    {
        get => _newGlobalServerName;
        set => SetProperty(ref _newGlobalServerName, value);
    }

    public string NewRepoServerName
    {
        get => _newRepoServerName;
        set => SetProperty(ref _newRepoServerName, value);
    }

    public string StatusMessage
    {
        get => _statusMessage;
        set => SetProperty(ref _statusMessage, value);
    }

    public bool IsBusy
    {
        get => _isBusy;
        set => SetProperty(ref _isBusy, value);
    }

    public async Task InitializeAsync(CancellationToken cancellationToken)
    {
        await RefreshAsync(cancellationToken);
    }

    public async Task RefreshAsync(CancellationToken cancellationToken)
    {
        IsBusy = true;
        try
        {
            string scopeKey = NormalizeScope(RepoScopeKey);
            McpPolicyScopeDto global = await _policyService.LoadGlobalPolicyAsync(cancellationToken);
            McpPolicyScopeDto repo = await _policyService.LoadRepoPolicyAsync(scopeKey, cancellationToken);
            McpPolicyPreviewDto preview = await _policyService.LoadMergedPreviewAsync(scopeKey, cancellationToken);

            HarnessPreferencesDto globalPreferences = NormalizePreferences(global.HarnessPreferences, useDefaults: true);
            HarnessPreferencesDto repoPreferences = NormalizePreferences(repo.HarnessPreferences, useDefaults: false);
            HarnessPreferencesDto effectivePreferences = NormalizePreferences(preview.HarnessPreferences, useDefaults: true);

            GlobalDiPreset = globalPreferences.DiPreset;
            GlobalLanguagePreset = globalPreferences.LanguagePreset;
            GlobalCustomDiDescriptor = globalPreferences.CustomDiDescriptor;
            GlobalInternalConcurrencyCap = FormatCap(globalPreferences.InternalConcurrencyCap, useDefaults: true);
            GlobalDownstreamConcurrencyCap = FormatCap(globalPreferences.DownstreamConcurrencyCap, useDefaults: true);
            GlobalDownstreamMcpMode = NormalizeText(globalPreferences.DownstreamMcpMode, "local-only");
            RepoDiPreset = repoPreferences.DiPreset;
            RepoLanguagePreset = repoPreferences.LanguagePreset;
            RepoCustomDiDescriptor = repoPreferences.CustomDiDescriptor;
            RepoInternalConcurrencyCap = FormatCap(repoPreferences.InternalConcurrencyCap, useDefaults: false);
            RepoDownstreamConcurrencyCap = FormatCap(repoPreferences.DownstreamConcurrencyCap, useDefaults: false);
            RepoDownstreamMcpMode = NormalizeText(repoPreferences.DownstreamMcpMode, string.Empty);
            EffectiveDiPreset = effectivePreferences.DiPreset;
            EffectiveLanguagePreset = effectivePreferences.LanguagePreset;
            EffectiveCustomDiDescriptor = effectivePreferences.CustomDiDescriptor;
            EffectiveInternalConcurrencyCap = FormatCap(effectivePreferences.InternalConcurrencyCap, useDefaults: true);
            EffectiveDownstreamConcurrencyCap = FormatCap(effectivePreferences.DownstreamConcurrencyCap, useDefaults: true);
            EffectiveDownstreamMcpMode = NormalizeText(effectivePreferences.DownstreamMcpMode, "local-only");

            string? selectedGlobalName = SelectedGlobalServerPolicy?.ServerName;
            string? selectedRepoName = SelectedRepoServerPolicy?.ServerName;
            SyncServerPolicies(_globalServerPolicies, global.Servers);
            SyncServerPolicies(_repoServerPolicies, repo.Servers);
            SelectedGlobalServerPolicy = ResolveSelection(_globalServerPolicies, selectedGlobalName);
            SelectedRepoServerPolicy = ResolveSelection(_repoServerPolicies, selectedRepoName);

            GlobalPolicyJson = JsonSerializer.Serialize(global, DesktopJson.Default);
            RepoPolicyJson = JsonSerializer.Serialize(repo, DesktopJson.Default);
            MergedPreviewJson = JsonSerializer.Serialize(preview, DesktopJson.Default);
            StatusMessage = $"Loaded MCP policy scopes. Effective harness profile: {EffectiveDiPreset}/{EffectiveLanguagePreset}.";
        }
        catch (Exception exception)
        {
            StatusMessage = exception.Message;
        }
        finally
        {
            IsBusy = false;
        }
    }

    public void AddGlobalServerPolicy()
    {
        string name = NormalizeText(NewGlobalServerName, string.Empty);
        if (string.IsNullOrWhiteSpace(name))
        {
            StatusMessage = "Provide a server name before adding a global policy.";
            return;
        }
        if (_globalServerPolicies.Any(policy => policy.ServerName.Equals(name, StringComparison.OrdinalIgnoreCase)))
        {
            StatusMessage = $"Global policy already contains '{name}'.";
            return;
        }
        var editor = new McpServerPolicyEditor(name, true, DefaultServerMode(name), string.Empty);
        _globalServerPolicies.Add(editor);
        SelectedGlobalServerPolicy = editor;
        NewGlobalServerName = string.Empty;
    }

    public void RemoveSelectedGlobalServerPolicy()
    {
        if (SelectedGlobalServerPolicy == null)
        {
            StatusMessage = "Select a global server policy to remove.";
            return;
        }
        _globalServerPolicies.Remove(SelectedGlobalServerPolicy);
        SelectedGlobalServerPolicy = _globalServerPolicies.FirstOrDefault();
    }

    public void AddRepoServerPolicy()
    {
        string name = NormalizeText(NewRepoServerName, string.Empty);
        if (string.IsNullOrWhiteSpace(name))
        {
            StatusMessage = "Provide a server name before adding a repo override.";
            return;
        }
        if (_repoServerPolicies.Any(policy => policy.ServerName.Equals(name, StringComparison.OrdinalIgnoreCase)))
        {
            StatusMessage = $"Repo overrides already contain '{name}'.";
            return;
        }
        var editor = new McpServerPolicyEditor(name, true, DefaultServerMode(name), string.Empty);
        _repoServerPolicies.Add(editor);
        SelectedRepoServerPolicy = editor;
        NewRepoServerName = string.Empty;
    }

    public void RemoveSelectedRepoServerPolicy()
    {
        if (SelectedRepoServerPolicy == null)
        {
            StatusMessage = "Select a repo server override to remove.";
            return;
        }
        _repoServerPolicies.Remove(SelectedRepoServerPolicy);
        SelectedRepoServerPolicy = _repoServerPolicies.FirstOrDefault();
    }

    public async Task SaveGlobalAsync(CancellationToken cancellationToken)
    {
        IsBusy = true;
        try
        {
            McpPolicyScopeDto policy = ApplyHarnessPreferences(
                DeserializeScope(GlobalPolicyJson),
                GlobalDiPreset,
                GlobalLanguagePreset,
                GlobalCustomDiDescriptor,
                GlobalInternalConcurrencyCap,
                GlobalDownstreamConcurrencyCap,
                GlobalDownstreamMcpMode,
                useDefaultsForCaps: true) with
            {
                ScopeKey = "global",
                UpdatedAt = DateTimeOffset.UtcNow
            };
            policy = ApplyServerPolicies(policy, _globalServerPolicies);
            await _policyService.SaveGlobalPolicyAsync(policy, cancellationToken);
            await RefreshAsync(cancellationToken);
            StatusMessage = "Saved global MCP policy scope.";
        }
        catch (Exception exception)
        {
            StatusMessage = exception.Message;
        }
        finally
        {
            IsBusy = false;
        }
    }

    public async Task SaveRepoAsync(CancellationToken cancellationToken)
    {
        IsBusy = true;
        try
        {
            string scopeKey = NormalizeScope(RepoScopeKey);
            McpPolicyScopeDto policy = ApplyHarnessPreferences(
                DeserializeScope(RepoPolicyJson),
                RepoDiPreset,
                RepoLanguagePreset,
                RepoCustomDiDescriptor,
                RepoInternalConcurrencyCap,
                RepoDownstreamConcurrencyCap,
                RepoDownstreamMcpMode,
                useDefaultsForCaps: false) with
            {
                ScopeKey = scopeKey,
                UpdatedAt = DateTimeOffset.UtcNow
            };
            policy = ApplyServerPolicies(policy, _repoServerPolicies);
            await _policyService.SaveRepoPolicyAsync(policy, cancellationToken);
            await RefreshAsync(cancellationToken);
            StatusMessage = $"Saved repo MCP policy scope '{scopeKey}'.";
        }
        catch (Exception exception)
        {
            StatusMessage = exception.Message;
        }
        finally
        {
            IsBusy = false;
        }
    }

    private static McpPolicyScopeDto DeserializeScope(string json)
    {
        McpPolicyScopeDto? value = JsonSerializer.Deserialize<McpPolicyScopeDto>(json, DesktopJson.Default);
        return value ?? throw new InvalidOperationException("The policy JSON payload could not be parsed.");
    }

    private static McpPolicyScopeDto ApplyHarnessPreferences(
        McpPolicyScopeDto scope,
        string diPreset,
        string languagePreset,
        string customDiDescriptor,
        string internalConcurrencyCap,
        string downstreamConcurrencyCap,
        string downstreamMcpMode,
        bool useDefaultsForCaps)
        => scope with
        {
            HarnessPreferences = new HarnessPreferencesDto(
                NormalizeText(diPreset, string.Empty),
                NormalizeText(languagePreset, string.Empty),
                NormalizeText(customDiDescriptor, string.Empty),
                scope.HarnessPreferences?.LintEnabled ?? false,
                scope.HarnessPreferences?.LintEngines ?? Array.Empty<string>(),
                NormalizeText(scope.HarnessPreferences?.LintStrictness, string.Empty),
                NormalizeText(scope.HarnessPreferences?.LintUnsupportedRepoPolicy, string.Empty),
                ParseCap(internalConcurrencyCap, useDefaultsForCaps),
                ParseCap(downstreamConcurrencyCap, useDefaultsForCaps),
                NormalizeText(downstreamMcpMode, string.Empty))
        };

    private static HarnessPreferencesDto NormalizePreferences(HarnessPreferencesDto? preferences, bool useDefaults)
        => new(
            NormalizeText(preferences?.DiPreset, useDefaults ? "service-loader" : string.Empty),
            NormalizeText(preferences?.LanguagePreset, useDefaults ? "java" : string.Empty),
            NormalizeText(preferences?.CustomDiDescriptor, string.Empty),
            preferences?.LintEnabled ?? useDefaults,
            NormalizeLintEngines(preferences?.LintEngines, useDefaults),
            NormalizeText(preferences?.LintStrictness, useDefaults ? "error" : string.Empty),
            NormalizeText(preferences?.LintUnsupportedRepoPolicy, useDefaults ? "fail" : string.Empty),
            NormalizeCap(preferences?.InternalConcurrencyCap, useDefaults),
            NormalizeCap(preferences?.DownstreamConcurrencyCap, useDefaults),
            NormalizeText(preferences?.DownstreamMcpMode, useDefaults ? "local-only" : string.Empty));

    private static IReadOnlyList<string> NormalizeLintEngines(IReadOnlyList<string>? lintEngines, bool useDefaults)
    {
        IReadOnlyList<string> fallback = useDefaults ? ["checkstyle", "pmd", "error-prone"] : [];
        IReadOnlyList<string> normalized = (lintEngines ?? [])
            .Where(value => !string.IsNullOrWhiteSpace(value))
            .Select(value => value.Trim().ToLowerInvariant())
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToArray();
        return normalized.Count > 0 ? normalized : fallback;
    }

    private static int? NormalizeCap(int? value, bool useDefaults)
    {
        if (value is null)
        {
            return useDefaults ? 0 : null;
        }

        return Math.Max(0, value.Value);
    }

    private static string FormatCap(int? value, bool useDefaults)
    {
        int? normalized = NormalizeCap(value, useDefaults);
        return normalized is null ? string.Empty : normalized.Value.ToString();
    }

    private static int? ParseCap(string value, bool useDefaults)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return useDefaults ? 0 : null;
        }

        if (!int.TryParse(value.Trim(), out int parsed))
        {
            return useDefaults ? 0 : null;
        }

        return Math.Max(0, parsed);
    }

    private static void SyncServerPolicies(
        ObservableCollection<McpServerPolicyEditor> target,
        IReadOnlyList<McpServerPolicyDto>? source)
    {
        target.Clear();
        if (source == null)
        {
            return;
        }
        foreach (McpServerPolicyDto policy in source)
        {
            string envJson = policy.Env == null || policy.Env.Count == 0
                ? string.Empty
                : JsonSerializer.Serialize(policy.Env, DesktopJson.Default);
            target.Add(new McpServerPolicyEditor(
                policy.ServerName,
                policy.Enabled,
                NormalizeText(policy.Mode, DefaultServerMode(policy.ServerName)),
                envJson));
        }
    }

    private static McpServerPolicyEditor? ResolveSelection(
        ObservableCollection<McpServerPolicyEditor> target,
        string? previousName)
    {
        if (!string.IsNullOrWhiteSpace(previousName))
        {
            McpServerPolicyEditor? match = target.FirstOrDefault(item =>
                item.ServerName.Equals(previousName, StringComparison.OrdinalIgnoreCase));
            if (match != null)
            {
                return match;
            }
        }
        return target.FirstOrDefault();
    }

    private static McpPolicyScopeDto ApplyServerPolicies(
        McpPolicyScopeDto scope,
        IEnumerable<McpServerPolicyEditor> policies)
    {
        List<McpServerPolicyDto> servers = new();
        foreach (McpServerPolicyEditor policy in policies)
        {
            if (string.IsNullOrWhiteSpace(policy.ServerName))
            {
                continue;
            }
            IReadOnlyDictionary<string, string>? envOverrides = ParseEnvOverrides(policy.EnvJson);
            servers.Add(new McpServerPolicyDto(
                policy.ServerName.Trim(),
                policy.Enabled,
                NormalizeText(policy.Mode, DefaultServerMode(policy.ServerName)),
                envOverrides));
        }
        return scope with { Servers = servers };
    }

    private static IReadOnlyDictionary<string, string>? ParseEnvOverrides(string envJson)
    {
        if (string.IsNullOrWhiteSpace(envJson))
        {
            return null;
        }
        try
        {
            Dictionary<string, string>? parsed =
                JsonSerializer.Deserialize<Dictionary<string, string>>(envJson, DesktopJson.Default);
            if (parsed == null || parsed.Count == 0)
            {
                return null;
            }
            return parsed;
        }
        catch (JsonException exception)
        {
            throw new InvalidOperationException("Server env overrides must be valid JSON.", exception);
        }
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

    private static string NormalizeText(string? value, string fallback)
        => string.IsNullOrWhiteSpace(value) ? fallback : value.Trim();

    private static string NormalizeScope(string scopeKey)
        => string.IsNullOrWhiteSpace(scopeKey) ? "workspace-default" : scopeKey.Trim();
}

public sealed class McpServerPolicyEditor : ObservableObject
{
    private string _serverName;
    private bool _enabled;
    private string _mode;
    private string _envJson;

    public McpServerPolicyEditor(string serverName, bool enabled, string mode, string envJson)
    {
        _serverName = serverName;
        _enabled = enabled;
        _mode = mode;
        _envJson = envJson;
    }

    public string ServerName
    {
        get => _serverName;
        set
        {
            if (SetProperty(ref _serverName, value))
            {
                OnPropertyChanged(nameof(DisplaySummary));
            }
        }
    }

    public bool Enabled
    {
        get => _enabled;
        set
        {
            if (SetProperty(ref _enabled, value))
            {
                OnPropertyChanged(nameof(DisplaySummary));
            }
        }
    }

    public string Mode
    {
        get => _mode;
        set
        {
            if (SetProperty(ref _mode, value))
            {
                OnPropertyChanged(nameof(DisplaySummary));
            }
        }
    }

    public string EnvJson
    {
        get => _envJson;
        set => SetProperty(ref _envJson, value);
    }

    public string DisplaySummary => string.IsNullOrWhiteSpace(ServerName)
        ? "(unnamed server)"
        : $"{ServerName} [{Mode}]";
}
