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

    public static string GetSessionRuntimeDirectory(string sessionId)
        => Path.Combine(RuntimeDirectory, sessionId);

    public static void EnsureCreated()
    {
        Directory.CreateDirectory(RootDirectory);
        Directory.CreateDirectory(SecretDirectory);
        Directory.CreateDirectory(DiffPreviewDirectory);
        Directory.CreateDirectory(RuntimeDirectory);
    }
}
