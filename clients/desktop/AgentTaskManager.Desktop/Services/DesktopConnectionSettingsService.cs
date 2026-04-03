using AgentTaskManager.Desktop.Contracts;
using System.Text.Json;

namespace AgentTaskManager.Desktop.Services;

public sealed class DesktopConnectionSettingsService : IDesktopConnectionSettingsService
{
    private readonly DesktopLocalBackendSupervisor _localBackendSupervisor;
    private readonly DesktopSshTunnelSupervisor _sshTunnelSupervisor;
    private readonly DesktopRemoteDefaultsDetector _remoteDefaultsDetector;
    private DesktopConnectionSettingsDto _current;

    public DesktopConnectionSettingsService(
        DesktopLocalBackendSupervisor localBackendSupervisor,
        DesktopSshTunnelSupervisor sshTunnelSupervisor,
        DesktopRemoteDefaultsDetector remoteDefaultsDetector)
    {
        _localBackendSupervisor = localBackendSupervisor;
        _sshTunnelSupervisor = sshTunnelSupervisor;
        _remoteDefaultsDetector = remoteDefaultsDetector;
        _current = LoadFromDisk();
    }

    public DesktopConnectionSettingsDto Current => _current;

    public async Task SaveAsync(DesktopConnectionSettingsDto settings, CancellationToken cancellationToken)
    {
        _current = Normalize(settings);
        DesktopStoragePaths.EnsureCreated();
        string json = JsonSerializer.Serialize(_current, DesktopJson.Default);
        await File.WriteAllTextAsync(DesktopStoragePaths.ConnectionSettingsFile, json, cancellationToken);
    }

    public Task ApplyDetectedRemoteDefaultsAsync(CancellationToken cancellationToken)
        => SaveAsync(
            _remoteDefaultsDetector.BuildDefaultSettings() with
            {
                CodexExecutablePath = _current.CodexExecutablePath,
                CodexHomePath = _current.CodexHomePath
            },
            cancellationToken);

    public Uri GetBackendBaseUri()
        => new(AppendTrailingSlash(BuildBackendBaseUrl(_current)));

    public async Task EnsureBackendTransportAsync(CancellationToken cancellationToken)
    {
        if (_current.ConnectionMode == DesktopConnectionModes.Local)
        {
            await _localBackendSupervisor.EnsureBackendAsync(_current, cancellationToken);
            return;
        }

        if (_current.ConnectionMode == DesktopConnectionModes.RemoteTunnel)
        {
            await _sshTunnelSupervisor.EnsureBackendTunnelAsync(_current, cancellationToken);
        }
    }

    public async Task StopBackendTransportAsync(CancellationToken cancellationToken)
    {
        await _localBackendSupervisor.StopAsync(cancellationToken);
        await _sshTunnelSupervisor.StopAsync(cancellationToken);
    }

    public string GetTransportStatus()
        => _current.ConnectionMode == DesktopConnectionModes.Local
            ? _localBackendSupervisor.BuildStatusSummary(_current)
            : _sshTunnelSupervisor.BuildStatusSummary(_current);

    public string MapWorkspacePathToLocal(string workspacePath)
    {
        if (string.IsNullOrWhiteSpace(workspacePath))
        {
            return workspacePath;
        }

        if (_current.ConnectionMode == DesktopConnectionModes.Local)
        {
            return workspacePath;
        }

        string remotePrefix = NormalizePathPrefix(_current.RemotePathPrefix, '/');
        string localPrefix = NormalizePathPrefix(_current.LocalPathPrefix, '\\');
        if (string.IsNullOrWhiteSpace(remotePrefix) || string.IsNullOrWhiteSpace(localPrefix))
        {
            return workspacePath;
        }

        if (!workspacePath.StartsWith(remotePrefix, StringComparison.OrdinalIgnoreCase))
        {
            return workspacePath;
        }

        string suffix = workspacePath[remotePrefix.Length..]
            .TrimStart('/', '\\')
            .Replace('/', '\\');
        return Path.Combine(localPrefix, suffix);
    }

    public IReadOnlyList<WorkspaceDescriptorDto> GetConfiguredWorkspaces()
    {
        if (_current.ConnectionMode == DesktopConnectionModes.Local)
        {
            return Array.Empty<WorkspaceDescriptorDto>();
        }

        if (string.IsNullOrWhiteSpace(_current.RemoteWorkspaceRoot))
        {
            return Array.Empty<WorkspaceDescriptorDto>();
        }

        return new[]
        {
            new WorkspaceDescriptorDto(
                _current.ProfileLabel,
                _current.RemoteWorkspaceRoot,
                string.IsNullOrWhiteSpace(_current.RemoteRepoPath)
                    ? _current.RemoteWorkspaceRoot
                    : _current.RemoteRepoPath,
                true,
                true,
                true,
                _current.PreferredProfileKey,
                DateTimeOffset.UtcNow)
        };
    }

    public bool ManageLocalRuntime()
        => _current.RuntimeMode == DesktopRuntimeModes.LocalManaged;

    public bool ObserveOnlySessions()
        => _current.RuntimeMode == DesktopRuntimeModes.ObserveOnly;

    public bool AllowRuntimeHandoff()
        => _current.AllowRuntimeHandoff;

    public bool CreateRuntimeByDefault()
        => _current.CreateRuntimeByDefault;

    public int GetSessionListLimit()
        => Math.Clamp(_current.SessionListLimit, 10, 200);

    public int GetEventReplayLimit()
        => Math.Clamp(_current.EventReplayLimit, 5, 200);

    private DesktopConnectionSettingsDto LoadFromDisk()
    {
        DesktopStoragePaths.EnsureCreated();
        if (!File.Exists(DesktopStoragePaths.ConnectionSettingsFile))
        {
            DesktopConnectionSettingsDto defaults = Normalize(_remoteDefaultsDetector.BuildDefaultSettings());
            File.WriteAllText(
                DesktopStoragePaths.ConnectionSettingsFile,
                JsonSerializer.Serialize(defaults, DesktopJson.Default));
            return defaults;
        }

        string json = File.ReadAllText(DesktopStoragePaths.ConnectionSettingsFile);
        DesktopConnectionSettingsDto? stored = JsonSerializer.Deserialize<DesktopConnectionSettingsDto>(json, DesktopJson.Default);
        return Normalize(stored ?? _remoteDefaultsDetector.BuildDefaultSettings());
    }

    private static DesktopConnectionSettingsDto Normalize(DesktopConnectionSettingsDto settings)
        => settings with
        {
            ProfileLabel = string.IsNullOrWhiteSpace(settings.ProfileLabel) ? "Workspace Connection" : settings.ProfileLabel.Trim(),
            ConnectionMode = NormalizeMode(settings.ConnectionMode),
            AuthMode = NormalizeAuthMode(settings.AuthMode),
            DirectBackendBaseUrl = NormalizeBaseUrl(settings.DirectBackendBaseUrl),
            RemoteHost = settings.RemoteHost.Trim(),
            RemoteSshPort = settings.RemoteSshPort <= 0 ? 22 : settings.RemoteSshPort,
            RemoteUser = string.IsNullOrWhiteSpace(settings.RemoteUser) ? "ubuntu" : settings.RemoteUser.Trim(),
            LocalTunnelPort = settings.LocalTunnelPort <= 0 ? 19000 : settings.LocalTunnelPort,
            RemoteBackendPort = settings.RemoteBackendPort <= 0 ? 9000 : settings.RemoteBackendPort,
            TunnelConnectTimeoutSeconds = settings.TunnelConnectTimeoutSeconds <= 0 ? 12 : settings.TunnelConnectTimeoutSeconds,
            RuntimeMode = NormalizeRuntimeMode(settings.RuntimeMode),
            SessionListLimit = Math.Clamp(settings.SessionListLimit <= 0 ? 50 : settings.SessionListLimit, 10, 200),
            EventReplayLimit = Math.Clamp(settings.EventReplayLimit <= 0 ? 20 : settings.EventReplayLimit, 5, 200),
            PreferredProfileKey = string.IsNullOrWhiteSpace(settings.PreferredProfileKey)
                ? "workspace-default"
                : settings.PreferredProfileKey.Trim(),
            PreferredModel = string.IsNullOrWhiteSpace(settings.PreferredModel)
                ? "gpt-5.3-codex"
                : settings.PreferredModel.Trim(),
            PreferredReasoningEffort = string.IsNullOrWhiteSpace(settings.PreferredReasoningEffort)
                ? "high"
                : settings.PreferredReasoningEffort.Trim(),
            ForwardedUserHeaderName = string.IsNullOrWhiteSpace(settings.ForwardedUserHeaderName)
                ? "X-Forwarded-User"
                : settings.ForwardedUserHeaderName.Trim(),
            CodexExecutablePath = settings.CodexExecutablePath?.Trim() ?? string.Empty,
            CodexHomePath = settings.CodexHomePath?.Trim() ?? string.Empty
        };

    private static string BuildBackendBaseUrl(DesktopConnectionSettingsDto settings)
    {
        if (settings.ConnectionMode == DesktopConnectionModes.RemoteTunnel)
        {
            string pathSuffix = ExtractBasePathSuffix(settings.DirectBackendBaseUrl);
            return $"http://127.0.0.1:{settings.LocalTunnelPort}{pathSuffix}";
        }

        return NormalizeBaseUrl(settings.DirectBackendBaseUrl);
    }

    private static string NormalizeMode(string mode)
        => mode switch
        {
            DesktopConnectionModes.RemoteTunnel => DesktopConnectionModes.RemoteTunnel,
            DesktopConnectionModes.RemoteDirect => DesktopConnectionModes.RemoteDirect,
            _ => DesktopConnectionModes.Local
        };

    private static string NormalizeAuthMode(string authMode)
        => authMode == DesktopAuthModes.TokenApi
            ? DesktopAuthModes.TokenApi
            : DesktopAuthModes.Basic;

    private static string NormalizeRuntimeMode(string runtimeMode)
        => runtimeMode switch
        {
            DesktopRuntimeModes.RemoteManaged => DesktopRuntimeModes.RemoteManaged,
            DesktopRuntimeModes.ObserveOnly => DesktopRuntimeModes.ObserveOnly,
            _ => DesktopRuntimeModes.LocalManaged
        };

    private static string NormalizeBaseUrl(string value)
    {
        string normalized = string.IsNullOrWhiteSpace(value) ? "http://127.0.0.1:9000" : value.Trim();
        return normalized.TrimEnd('/');
    }

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

    private static string AppendTrailingSlash(string value)
        => value.EndsWith("/", StringComparison.Ordinal) ? value : $"{value}/";

    private static string NormalizePathPrefix(string value, char separator)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return string.Empty;
        }

        return value.Trim().TrimEnd(separator);
    }
}
