namespace AgentTaskManager.Desktop.Contracts;

public static class DesktopConnectionModes
{
    public const string Local = "LOCAL";
    public const string RemoteTunnel = "REMOTE_TUNNEL";
    public const string RemoteDirect = "REMOTE_DIRECT";
}

public static class DesktopAuthModes
{
    public const string Basic = "BASIC";
    public const string TokenApi = "TOKEN_API";
}

public static class DesktopRuntimeModes
{
    public const string LocalManaged = "LOCAL_MANAGED";
    public const string RemoteManaged = "REMOTE_MANAGED";
    public const string ObserveOnly = "OBSERVE_ONLY";
}

public sealed record DesktopConnectionSettingsDto(
    string ProfileLabel,
    string ConnectionMode,
    string AuthMode,
    string DirectBackendBaseUrl,
    string RemoteHost,
    int RemoteSshPort,
    string RemoteUser,
    string SshKeyPath,
    int LocalTunnelPort,
    int RemoteBackendPort,
    bool AutoStartTunnel,
    int TunnelConnectTimeoutSeconds,
    string RuntimeMode,
    bool AllowRuntimeHandoff,
    bool CreateRuntimeByDefault,
    bool IncludeLocalWorkspaceCatalog,
    int SessionListLimit,
    int EventReplayLimit,
    string RemoteWorkspaceRoot,
    string RemoteRepoPath,
    string RemotePathPrefix,
    string LocalPathPrefix,
    string PreferredProfileKey,
    string PreferredModel,
    string PreferredReasoningEffort,
    bool SendForwardedUserHeader,
    string ForwardedUserHeaderName,
    string Notes,
    string CodexExecutablePath,
    string CodexHomePath);
