using AgentTaskManager.Desktop.Contracts;
using AgentTaskManager.Desktop.Services;
using CommunityToolkit.Mvvm.ComponentModel;

namespace AgentTaskManager.Desktop.ViewModels;

public partial class ConnectionSettingsViewModel : ObservableObject
{
    private readonly IDesktopConnectionSettingsService _connectionSettingsService;
    private bool _initialized;

    public ConnectionSettingsViewModel(IDesktopConnectionSettingsService connectionSettingsService)
    {
        _connectionSettingsService = connectionSettingsService;
    }

    public IReadOnlyList<string> ConnectionModes { get; } =
        [DesktopConnectionModes.Local, DesktopConnectionModes.RemoteTunnel, DesktopConnectionModes.RemoteDirect];

    public IReadOnlyList<string> AuthModes { get; } =
        [DesktopAuthModes.Basic, DesktopAuthModes.TokenApi];

    public IReadOnlyList<string> RuntimeModes { get; } =
        [DesktopRuntimeModes.LocalManaged, DesktopRuntimeModes.RemoteManaged, DesktopRuntimeModes.ObserveOnly];

    [ObservableProperty] private string _profileLabel = string.Empty;
    [ObservableProperty] private string _connectionMode = DesktopConnectionModes.Local;
    [ObservableProperty] private string _authMode = DesktopAuthModes.Basic;
    [ObservableProperty] private string _directBackendBaseUrl = "http://127.0.0.1:9000";
    [ObservableProperty] private string _remoteHost = string.Empty;
    [ObservableProperty] private int _remoteSshPort = 22;
    [ObservableProperty] private string _remoteUser = "ubuntu";
    [ObservableProperty] private string _sshKeyPath = string.Empty;
    [ObservableProperty] private int _localTunnelPort = 19000;
    [ObservableProperty] private int _remoteBackendPort = 9000;
    [ObservableProperty] private bool _autoStartTunnel = true;
    [ObservableProperty] private int _tunnelConnectTimeoutSeconds = 12;
    [ObservableProperty] private string _runtimeMode = DesktopRuntimeModes.LocalManaged;
    [ObservableProperty] private bool _allowRuntimeHandoff = true;
    [ObservableProperty] private bool _createRuntimeByDefault = true;
    [ObservableProperty] private bool _includeLocalWorkspaceCatalog = true;
    [ObservableProperty] private int _sessionListLimit = 50;
    [ObservableProperty] private int _eventReplayLimit = 20;
    [ObservableProperty] private string _remoteWorkspaceRoot = string.Empty;
    [ObservableProperty] private string _remoteRepoPath = string.Empty;
    [ObservableProperty] private string _remotePathPrefix = "/srv";
    [ObservableProperty] private string _localPathPrefix = @"F:\NovusRemote";
    [ObservableProperty] private string _preferredProfileKey = "workspace-default";
    [ObservableProperty] private string _preferredModel = "gpt-5.3-codex";
    [ObservableProperty] private string _preferredReasoningEffort = "high";
    [ObservableProperty] private bool _sendForwardedUserHeader = true;
    [ObservableProperty] private string _forwardedUserHeaderName = "X-Forwarded-User";
    [ObservableProperty] private string _notes = string.Empty;
    [ObservableProperty] private string _effectiveBackendBaseUrl = "http://127.0.0.1:9000";
    [ObservableProperty] private string _transportStatus = "Local backend http://127.0.0.1:9000.";
    [ObservableProperty] private string _modeSummary = "Local mode with desktop-managed runtime.";

    public DesktopConnectionSettingsDto CurrentSettings => BuildSettings();

    public async Task InitializeAsync(CancellationToken cancellationToken)
    {
        if (_initialized)
        {
            return;
        }

        Load(_connectionSettingsService.Current);
        _initialized = true;
        await Task.CompletedTask;
    }

    public async Task SaveAsync(CancellationToken cancellationToken)
    {
        await _connectionSettingsService.SaveAsync(BuildSettings(), cancellationToken);
        Load(_connectionSettingsService.Current);
    }

    public async Task ApplyDetectedRemoteDefaultsAsync(CancellationToken cancellationToken)
    {
        await _connectionSettingsService.ApplyDetectedRemoteDefaultsAsync(cancellationToken);
        Load(_connectionSettingsService.Current);
    }

    public async Task EnsureTransportAsync(CancellationToken cancellationToken)
    {
        await SaveAsync(cancellationToken);
        await _connectionSettingsService.EnsureBackendTransportAsync(cancellationToken);
        RefreshDerivedState();
    }

    public async Task StopTransportAsync(CancellationToken cancellationToken)
    {
        await _connectionSettingsService.StopBackendTransportAsync(cancellationToken);
        RefreshDerivedState();
    }

    public void UseLocalMode()
    {
        ProfileLabel = "Local Workspace";
        ConnectionMode = DesktopConnectionModes.Local;
        AuthMode = DesktopAuthModes.Basic;
        RuntimeMode = DesktopRuntimeModes.LocalManaged;
        DirectBackendBaseUrl = "http://127.0.0.1:9000";
        AutoStartTunnel = false;
        CreateRuntimeByDefault = true;
        IncludeLocalWorkspaceCatalog = true;
        RefreshDerivedState();
    }

    public void UseRemoteMode()
    {
        ProfileLabel = "Project Novus Remote";
        ConnectionMode = DesktopConnectionModes.RemoteTunnel;
        AuthMode = DesktopAuthModes.Basic;
        RuntimeMode = DesktopRuntimeModes.RemoteManaged;
        if (string.IsNullOrWhiteSpace(ExtractBasePathSuffix(DirectBackendBaseUrl)))
        {
            DirectBackendBaseUrl = "http://127.0.0.1:9000/agent-task-manager";
        }

        AutoStartTunnel = true;
        CreateRuntimeByDefault = false;
        IncludeLocalWorkspaceCatalog = false;
        RefreshDerivedState();
    }

    public void ApplyTo(SignInViewModel signIn, WorkspacePickerViewModel workspacePicker)
    {
        signIn.BackendUrl = EffectiveBackendBaseUrl;
        string workspaceRoot = ConnectionMode == DesktopConnectionModes.Local
            ? string.Empty
            : CurrentSettings.RemoteWorkspaceRoot;
        string repoPath = ConnectionMode == DesktopConnectionModes.Local
            ? string.Empty
            : CurrentSettings.RemoteRepoPath;
        workspacePicker.ApplyConnectionDefaults(
            workspaceRoot,
            repoPath,
            CurrentSettings.PreferredProfileKey,
            CurrentSettings.CreateRuntimeByDefault);
    }

    public string BuildConnectionSummary()
        => $"{ProfileLabel} | {ConnectionMode} | {EffectiveBackendBaseUrl}";

    partial void OnConnectionModeChanged(string value) => RefreshDerivedState();
    partial void OnDirectBackendBaseUrlChanged(string value) => RefreshDerivedState();
    partial void OnLocalTunnelPortChanged(int value) => RefreshDerivedState();
    partial void OnRuntimeModeChanged(string value) => RefreshDerivedState();
    partial void OnRemoteHostChanged(string value) => RefreshDerivedState();

    private DesktopConnectionSettingsDto BuildSettings()
        => new(
            ProfileLabel,
            ConnectionMode,
            AuthMode,
            DirectBackendBaseUrl,
            RemoteHost,
            RemoteSshPort,
            RemoteUser,
            SshKeyPath,
            LocalTunnelPort,
            RemoteBackendPort,
            AutoStartTunnel,
            TunnelConnectTimeoutSeconds,
            RuntimeMode,
            AllowRuntimeHandoff,
            CreateRuntimeByDefault,
            IncludeLocalWorkspaceCatalog,
            SessionListLimit,
            EventReplayLimit,
            RemoteWorkspaceRoot,
            RemoteRepoPath,
            RemotePathPrefix,
            LocalPathPrefix,
            PreferredProfileKey,
            PreferredModel,
            PreferredReasoningEffort,
            SendForwardedUserHeader,
            ForwardedUserHeaderName,
            Notes,
            _connectionSettingsService.Current.CodexExecutablePath,
            _connectionSettingsService.Current.CodexHomePath);

    private void Load(DesktopConnectionSettingsDto settings)
    {
        ProfileLabel = settings.ProfileLabel;
        ConnectionMode = settings.ConnectionMode;
        AuthMode = settings.AuthMode;
        DirectBackendBaseUrl = settings.DirectBackendBaseUrl;
        RemoteHost = settings.RemoteHost;
        RemoteSshPort = settings.RemoteSshPort;
        RemoteUser = settings.RemoteUser;
        SshKeyPath = settings.SshKeyPath;
        LocalTunnelPort = settings.LocalTunnelPort;
        RemoteBackendPort = settings.RemoteBackendPort;
        AutoStartTunnel = settings.AutoStartTunnel;
        TunnelConnectTimeoutSeconds = settings.TunnelConnectTimeoutSeconds;
        RuntimeMode = settings.RuntimeMode;
        AllowRuntimeHandoff = settings.AllowRuntimeHandoff;
        CreateRuntimeByDefault = settings.CreateRuntimeByDefault;
        IncludeLocalWorkspaceCatalog = settings.IncludeLocalWorkspaceCatalog;
        SessionListLimit = settings.SessionListLimit;
        EventReplayLimit = settings.EventReplayLimit;
        RemoteWorkspaceRoot = settings.RemoteWorkspaceRoot;
        RemoteRepoPath = settings.RemoteRepoPath;
        RemotePathPrefix = settings.RemotePathPrefix;
        LocalPathPrefix = settings.LocalPathPrefix;
        PreferredProfileKey = settings.PreferredProfileKey;
        PreferredModel = settings.PreferredModel;
        PreferredReasoningEffort = settings.PreferredReasoningEffort;
        SendForwardedUserHeader = settings.SendForwardedUserHeader;
        ForwardedUserHeaderName = settings.ForwardedUserHeaderName;
        Notes = settings.Notes;
        RefreshDerivedState();
    }

    private void RefreshDerivedState()
    {
        EffectiveBackendBaseUrl = BuildEffectiveBackendBaseUrl();
        TransportStatus = _connectionSettingsService.GetTransportStatus();
        ModeSummary = (ConnectionMode, RuntimeMode) switch
        {
            (DesktopConnectionModes.RemoteDirect, DesktopRuntimeModes.ObserveOnly) => "Remote backend selected in observe-only mode.",
            (DesktopConnectionModes.RemoteDirect, _) => "Remote backend selected with a backend-managed runtime.",
            (DesktopConnectionModes.RemoteTunnel, DesktopRuntimeModes.ObserveOnly) => "Remote backend selected through an SSH tunnel in observe-only mode.",
            (DesktopConnectionModes.RemoteTunnel, _) => "Remote backend selected through an SSH tunnel.",
            (_, DesktopRuntimeModes.ObserveOnly) => "Local backend selected in observe-only mode.",
            _ => "Local backend selected with desktop-managed runtime."
        };
    }

    private string BuildEffectiveBackendBaseUrl()
    {
        if (ConnectionMode != DesktopConnectionModes.RemoteTunnel)
        {
            return NormalizeBaseUrl(DirectBackendBaseUrl);
        }

        string pathSuffix = ExtractBasePathSuffix(DirectBackendBaseUrl);
        return $"http://127.0.0.1:{LocalTunnelPort}{pathSuffix}";
    }

    private static string NormalizeBaseUrl(string value)
        => string.IsNullOrWhiteSpace(value)
            ? "http://127.0.0.1:9000"
            : value.Trim().TrimEnd('/');

    private static string ExtractBasePathSuffix(string configuredBaseUrl)
    {
        string normalized = NormalizeBaseUrl(configuredBaseUrl);
        if (!Uri.TryCreate(normalized, UriKind.Absolute, out Uri? uri))
        {
            return string.Empty;
        }

        string path = uri.AbsolutePath.TrimEnd('/');
        return string.Equals(path, "/", StringComparison.Ordinal) ? string.Empty : path;
    }
}
