using AgentTaskManager.Desktop.Contracts;
using System.Text.RegularExpressions;

namespace AgentTaskManager.Desktop.Services;

public sealed class DesktopRemoteDefaultsDetector
{
    private static readonly Regex SshTargetPattern = new(@"(?<user>[\w.-]+)@(?<host>[\w\.-]+)", RegexOptions.Compiled);
    private static readonly Regex KeyPattern = new(@"-i\s+'(?<key>[^']+)'", RegexOptions.Compiled);
    private static readonly Regex MountPattern = new(@"mount\s+'.+?'\s+'(?<mount>[A-Za-z]:\\[^']+)'", RegexOptions.Compiled);

    public DesktopConnectionSettingsDto BuildDefaultSettings()
    {
        string userProfile = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        string sshRoot = Path.Combine(userProfile, "Documents", ".ssh");
        string keyPath = Path.Combine(sshRoot, "NovusKey.key");

        string remoteHost = string.Empty;
        string remoteUser = "ubuntu";
        string mountRoot = @"F:\NovusRemote";
        string? localRepoRoot = TryFindLocalRepoRoot("AgentTaskManager");

        foreach (string candidate in new[]
                 {
                     Path.Combine(sshRoot, "mongo_tunnel.ps1"),
                     Path.Combine(sshRoot, "postgres_novus_tunnel.ps1")
                 })
        {
            if (!File.Exists(candidate))
            {
                continue;
            }

            string content = File.ReadAllText(candidate);
            Match targetMatch = SshTargetPattern.Match(content);
            Match keyMatch = KeyPattern.Match(content);
            if (targetMatch.Success)
            {
                remoteUser = targetMatch.Groups["user"].Value;
                remoteHost = targetMatch.Groups["host"].Value;
            }

            if (keyMatch.Success)
            {
                keyPath = keyMatch.Groups["key"].Value;
            }
        }

        string mountScript = Path.Combine(sshRoot, "Mount Scripts", "mount-novus-remote.ps1");
        if (File.Exists(mountScript))
        {
            Match mountMatch = MountPattern.Match(File.ReadAllText(mountScript));
            if (mountMatch.Success)
            {
                mountRoot = mountMatch.Groups["mount"].Value;
            }
        }

        if (!string.IsNullOrWhiteSpace(localRepoRoot))
        {
            DirectoryInfo? parent = Directory.GetParent(localRepoRoot);
            if (parent != null)
            {
                mountRoot = parent.FullName;
            }
        }

        return new DesktopConnectionSettingsDto(
            ProfileLabel: string.IsNullOrWhiteSpace(remoteHost) ? "Local Workspace" : "Project Novus Remote",
            ConnectionMode: string.IsNullOrWhiteSpace(remoteHost) ? DesktopConnectionModes.Local : DesktopConnectionModes.RemoteTunnel,
            AuthMode: DesktopAuthModes.Basic,
            DirectBackendBaseUrl: string.IsNullOrWhiteSpace(remoteHost)
                ? "http://127.0.0.1:9000"
                : "http://127.0.0.1:9000/tavall-ai",
            RemoteHost: remoteHost,
            RemoteSshPort: 22,
            RemoteUser: remoteUser,
            SshKeyPath: keyPath,
            LocalTunnelPort: 19000,
            RemoteBackendPort: 9000,
            AutoStartTunnel: !string.IsNullOrWhiteSpace(remoteHost),
            TunnelConnectTimeoutSeconds: 12,
            RuntimeMode: string.IsNullOrWhiteSpace(remoteHost)
                ? DesktopRuntimeModes.LocalManaged
                : DesktopRuntimeModes.RemoteManaged,
            AllowRuntimeHandoff: true,
            CreateRuntimeByDefault: string.IsNullOrWhiteSpace(remoteHost),
            IncludeLocalWorkspaceCatalog: string.IsNullOrWhiteSpace(remoteHost),
            SessionListLimit: 50,
            EventReplayLimit: 20,
            RemoteWorkspaceRoot: "/srv/AgentTaskManager",
            RemoteRepoPath: "/srv/AgentTaskManager",
            RemotePathPrefix: "/srv",
            LocalPathPrefix: mountRoot,
            PreferredProfileKey: "workspace-default",
            PreferredModel: "gpt-5.3-codex",
            PreferredReasoningEffort: "high",
            SendForwardedUserHeader: true,
            ForwardedUserHeaderName: "X-Forwarded-User",
            Notes: BuildNotes(localRepoRoot),
            CodexExecutablePath: string.Empty,
            CodexHomePath: string.Empty);
    }

    private static string BuildNotes(string? localRepoRoot)
        => string.IsNullOrWhiteSpace(localRepoRoot)
            ? "Detected from Documents\\.ssh tunnel and mount scripts."
            : $"Detected from Documents\\.ssh assets and local repo root {localRepoRoot}.";

    private static string? TryFindLocalRepoRoot(string repoName)
    {
        DirectoryInfo? current = new(AppContext.BaseDirectory);
        while (current != null)
        {
            bool nameMatches = string.Equals(current.Name, repoName, StringComparison.OrdinalIgnoreCase);
            bool looksLikeRepoRoot = File.Exists(Path.Combine(current.FullName, "settings.gradle.kts"))
                || File.Exists(Path.Combine(current.FullName, $"{repoName}.sln"));
            if (nameMatches && looksLikeRepoRoot)
            {
                return current.FullName;
            }

            current = current.Parent;
        }

        return null;
    }
}
