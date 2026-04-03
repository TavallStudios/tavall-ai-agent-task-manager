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

    public CodexWorkspaceConfigurationService(
        ICodexEnvironmentService codexEnvironmentService,
        IDesktopConnectionSettingsService connectionSettingsService)
    {
        _codexEnvironmentService = codexEnvironmentService;
        _connectionSettingsService = connectionSettingsService;
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
            ["AGENTTASKMANAGER_CODEX_AUTH_MODE"] = setup.AuthMode
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
            binding.McpServers.Select(server => server.Name).ToList(),
            binding.ConfigLayers,
            binding.ApprovalPolicy,
            binding.SandboxMode,
            sessionRuntimeDirectory,
            true,
            true,
            $"Launches the official OpenAI codex app-server locally using CODEX_HOME={setup.CodexHomePath}.");
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
