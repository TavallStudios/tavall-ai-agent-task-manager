using AgentTaskManager.Desktop.Contracts;
using AgentTaskManager.Desktop.Services;
using CommunityToolkit.Mvvm.ComponentModel;
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
    private string _repoDiPreset = string.Empty;
    private string _repoLanguagePreset = string.Empty;
    private string _repoCustomDiDescriptor = string.Empty;
    private string _effectiveDiPreset = "service-loader";
    private string _effectiveLanguagePreset = "java";
    private string _effectiveCustomDiDescriptor = string.Empty;
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
            RepoDiPreset = repoPreferences.DiPreset;
            RepoLanguagePreset = repoPreferences.LanguagePreset;
            RepoCustomDiDescriptor = repoPreferences.CustomDiDescriptor;
            EffectiveDiPreset = effectivePreferences.DiPreset;
            EffectiveLanguagePreset = effectivePreferences.LanguagePreset;
            EffectiveCustomDiDescriptor = effectivePreferences.CustomDiDescriptor;

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

    public async Task SaveGlobalAsync(CancellationToken cancellationToken)
    {
        IsBusy = true;
        try
        {
            McpPolicyScopeDto policy = ApplyHarnessPreferences(
                DeserializeScope(GlobalPolicyJson),
                GlobalDiPreset,
                GlobalLanguagePreset,
                GlobalCustomDiDescriptor) with
            {
                ScopeKey = "global",
                UpdatedAt = DateTimeOffset.UtcNow
            };
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
                RepoCustomDiDescriptor) with
            {
                ScopeKey = scopeKey,
                UpdatedAt = DateTimeOffset.UtcNow
            };
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
        string customDiDescriptor)
        => scope with
        {
            HarnessPreferences = new HarnessPreferencesDto(
                NormalizeText(diPreset, string.Empty),
                NormalizeText(languagePreset, string.Empty),
                NormalizeText(customDiDescriptor, string.Empty),
                scope.HarnessPreferences?.LintEnabled ?? false,
                scope.HarnessPreferences?.LintEngines ?? Array.Empty<string>(),
                NormalizeText(scope.HarnessPreferences?.LintStrictness, string.Empty),
                NormalizeText(scope.HarnessPreferences?.LintUnsupportedRepoPolicy, string.Empty))
        };

    private static HarnessPreferencesDto NormalizePreferences(HarnessPreferencesDto? preferences, bool useDefaults)
        => new(
            NormalizeText(preferences?.DiPreset, useDefaults ? "service-loader" : string.Empty),
            NormalizeText(preferences?.LanguagePreset, useDefaults ? "java" : string.Empty),
            NormalizeText(preferences?.CustomDiDescriptor, string.Empty),
            preferences?.LintEnabled ?? useDefaults,
            NormalizeLintEngines(preferences?.LintEngines, useDefaults),
            NormalizeText(preferences?.LintStrictness, useDefaults ? "error" : string.Empty),
            NormalizeText(preferences?.LintUnsupportedRepoPolicy, useDefaults ? "fail" : string.Empty));

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

    private static string NormalizeText(string? value, string fallback)
        => string.IsNullOrWhiteSpace(value) ? fallback : value.Trim();

    private static string NormalizeScope(string scopeKey)
        => string.IsNullOrWhiteSpace(scopeKey) ? "workspace-default" : scopeKey.Trim();
}
