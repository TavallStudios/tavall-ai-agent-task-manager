namespace AgentTaskManager.Desktop.Services;

public static class DesktopStoragePaths
{
    private static readonly string Root = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "AgentTaskManager",
        "Desktop");

    public static string RootDirectory => Root;

    public static string SecretDirectory => Path.Combine(Root, "Secrets");

    public static string DiffPreviewDirectory => Path.Combine(Root, "Diffs");

    public static string RuntimeDirectory => Path.Combine(Root, "Runtime");

    public static string AuthSessionFile => Path.Combine(Root, "auth-session.json");

    public static string ConnectionSettingsFile => Path.Combine(Root, "connection-settings.json");

    public static string WorkspaceRegistryFile => Path.Combine(Root, "workspaces.json");

    public static string McpPolicyDirectory => Path.Combine(Root, "McpPolicy");

    public static string McpPolicyGlobalFile => Path.Combine(McpPolicyDirectory, "global-policy.json");

    public static string GetMcpPolicyRepoFile(string scopeKey)
        => Path.Combine(McpPolicyDirectory, $"{Sanitize(scopeKey)}.json");

    public static string RemoteRunnerProfilesFile => Path.Combine(Root, "remote-runner-profiles.json");

    public static string GetSessionRuntimeDirectory(string sessionId)
        => Path.Combine(RuntimeDirectory, sessionId);

    public static void EnsureCreated()
    {
        Directory.CreateDirectory(RootDirectory);
        Directory.CreateDirectory(SecretDirectory);
        Directory.CreateDirectory(DiffPreviewDirectory);
        Directory.CreateDirectory(RuntimeDirectory);
        Directory.CreateDirectory(McpPolicyDirectory);
    }

    private static string Sanitize(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return "default";
        }

        char[] invalid = Path.GetInvalidFileNameChars();
        return string.Concat(value.Select(character => invalid.Contains(character) ? '_' : character));
    }
}
