using AgentTaskManager.Desktop.Contracts;
using System.Net;
using System.Net.Sockets;
using System.Text.Json;

namespace AgentTaskManager.Desktop.Services;

public sealed class CodexWorkspaceConfigurationService : ICodexWorkspaceConfigurationService
{
    private const string OriginatorOverride = "agenttaskmanager_desktop";
    private readonly ICodexEnvironmentService _codexEnvironmentService;
    private readonly IDesktopConnectionSettingsService _connectionSettingsService;
    private readonly IMcpPolicyService _mcpPolicyService;

    public CodexWorkspaceConfigurationService(
        ICodexEnvironmentService codexEnvironmentService,
        IDesktopConnectionSettingsService connectionSettingsService,
        IMcpPolicyService mcpPolicyService)
    {
        _codexEnvironmentService = codexEnvironmentService;
        _connectionSettingsService = connectionSettingsService;
        _mcpPolicyService = mcpPolicyService;
    }

    public async Task<RuntimeLaunchEnvelopeDto> BuildLaunchEnvelopeAsync(SessionDetailDto session, CancellationToken cancellationToken)
    {
        WorkspaceBindingDto binding = session.WorkspaceBinding;
        DesktopStoragePaths.EnsureCreated();
        DesktopConnectionSettingsDto settings = _connectionSettingsService.Current;
        CodexLocalSetupDto setup = await _codexEnvironmentService.ResolveSetupAsync(
            settings.CodexExecutablePath,
            settings.CodexHomePath,
            cancellationToken);

        string sessionRuntimeDirectory = DesktopStoragePaths.GetSessionRuntimeDirectory(session.Summary.SessionId);
        Directory.CreateDirectory(sessionRuntimeDirectory);

        int port = ReserveLoopbackPort();
        string listenUri = $"ws://127.0.0.1:{port}";
        string preferredModel = string.IsNullOrWhiteSpace(session.RuntimeConnection.PreferredModel)
            ? "gpt-5.3-codex"
            : session.RuntimeConnection.PreferredModel;
        McpPolicyPreviewDto? mergedPolicy = await TryLoadMergedPolicyAsync(binding.ProfileKey, cancellationToken);
        IReadOnlyList<string> requiredServers = mergedPolicy is { EnabledServers.Count: > 0 }
            ? mergedPolicy.EnabledServers
            : binding.McpServers.Select(server => server.Name).ToList();
        HarnessPreferencesDto harnessPreferences = mergedPolicy?.HarnessPreferences
            ?? new HarnessPreferencesDto("service-loader", "java", string.Empty, true, ["checkstyle", "pmd", "error-prone"], "error", "fail", 0, 0);

        var arguments = new List<string>
        {
            "app-server",
            "--listen",
            listenUri,
            "--session-source",
            "desktop"
        };

        var environment = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            ["CODEX_INTERNAL_ORIGINATOR_OVERRIDE"] = OriginatorOverride,
            ["CODEX_HOME"] = setup.CodexHomePath,
            ["AGENTTASKMANAGER_SESSION_ID"] = session.Summary.SessionId,
            ["AGENTTASKMANAGER_RUNTIME_ID"] = session.RuntimeConnection.RuntimeId,
            ["AGENTTASKMANAGER_CODEX_AUTH_MODE"] = setup.AuthMode,
            ["AGENTTASKMANAGER_MCP_POLICY_SCOPE"] = binding.ProfileKey,
            ["AGENTTASKMANAGER_MCP_POLICY_ENABLED_TOOLS"] = mergedPolicy is { EnabledTools.Count: > 0 }
                ? string.Join(",", mergedPolicy.EnabledTools)
                : string.Empty,
            ["AGENTTASKMANAGER_HARNESS_DI_PRESET"] = harnessPreferences.DiPreset,
            ["AGENTTASKMANAGER_HARNESS_LANGUAGE_PRESET"] = harnessPreferences.LanguagePreset,
            ["AGENTTASKMANAGER_HARNESS_CUSTOM_DI_DESCRIPTOR"] = harnessPreferences.CustomDiDescriptor,
            ["AGENTTASKMANAGER_HARNESS_LINT_ENABLED"] = (harnessPreferences.LintEnabled ?? true) ? "true" : "false",
            ["AGENTTASKMANAGER_HARNESS_LINT_ENGINES"] = string.Join(",", harnessPreferences.LintEngines),
            ["AGENTTASKMANAGER_HARNESS_LINT_STRICTNESS"] = harnessPreferences.LintStrictness,
            ["AGENTTASKMANAGER_HARNESS_LINT_UNSUPPORTED_REPO_POLICY"] = harnessPreferences.LintUnsupportedRepoPolicy
        };

        await PersistSnapshotAsync(
            sessionRuntimeDirectory,
            session,
            setup.ExecutablePath,
            arguments,
            environment,
            listenUri,
            preferredModel,
            cancellationToken);

        return new RuntimeLaunchEnvelopeDto(
            session.Summary.SessionId,
            session.RuntimeConnection.RuntimeId,
            "CodexAppServer",
            "WEBSOCKET",
            setup.ExecutablePath,
            arguments,
            environment,
            listenUri,
            binding.WorkspaceRoot,
            binding.WorkingDirectory,
            binding.ProfileKey,
            preferredModel,
            requiredServers,
            binding.ConfigLayers,
            binding.ApprovalPolicy,
            binding.SandboxMode,
            sessionRuntimeDirectory,
            true,
            true,
            $"Launches the official OpenAI codex app-server locally using CODEX_HOME={setup.CodexHomePath}.");
    }

    private async Task<McpPolicyPreviewDto?> TryLoadMergedPolicyAsync(string scopeKey, CancellationToken cancellationToken)
    {
        try
        {
            return await _mcpPolicyService.LoadMergedPreviewAsync(scopeKey, cancellationToken);
        }
        catch
        {
            return null;
        }
    }

    private static async Task PersistSnapshotAsync(
        string sessionRuntimeDirectory,
        SessionDetailDto session,
        string commandPath,
        IReadOnlyList<string> arguments,
        IReadOnlyDictionary<string, string> environment,
        string listenUri,
        string preferredModel,
        CancellationToken cancellationToken)
    {
        var snapshot = new
        {
            sessionId = session.Summary.SessionId,
            runtimeId = session.RuntimeConnection.RuntimeId,
            workspaceRoot = session.WorkspaceBinding.WorkspaceRoot,
            workingDirectory = session.WorkspaceBinding.WorkingDirectory,
            profileKey = session.WorkspaceBinding.ProfileKey,
            preferredModel,
            approvalPolicy = session.WorkspaceBinding.ApprovalPolicy,
            sandboxMode = session.WorkspaceBinding.SandboxMode,
            commandPath,
            arguments,
            environment,
            listenUri,
            configLayers = session.WorkspaceBinding.ConfigLayers,
            mcpServers = session.WorkspaceBinding.McpServers
        };

        string json = JsonSerializer.Serialize(snapshot, DesktopJson.Default);
        string filePath = Path.Combine(sessionRuntimeDirectory, "runtime-launch.json");
        await File.WriteAllTextAsync(filePath, json, cancellationToken);
    }

    private static int ReserveLoopbackPort()
    {
        using var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        return ((IPEndPoint)listener.LocalEndpoint).Port;
    }
}
