using AgentTaskManager.Desktop.Contracts;
using System.Text.Json;

namespace AgentTaskManager.Desktop.Services;

public sealed class WorkspaceRegistryService : IWorkspaceRegistryService
{
    private readonly IDesktopConnectionSettingsService _connectionSettingsService;

    public WorkspaceRegistryService(IDesktopConnectionSettingsService connectionSettingsService)
    {
        _connectionSettingsService = connectionSettingsService;
    }

    public async Task<IReadOnlyList<WorkspaceDescriptorDto>> ListWorkspaceRootsAsync(CancellationToken cancellationToken)
    {
        DesktopStoragePaths.EnsureCreated();
        List<WorkspaceDescriptorDto> known = await LoadAsync(cancellationToken);
        if (_connectionSettingsService.Current.ConnectionMode == DesktopConnectionModes.Local)
        {
            string remotePrefix = _connectionSettingsService.Current.RemotePathPrefix;
            known = known
                .Where(item => IsUsableLocalWorkspace(item.WorkspaceRoot, remotePrefix))
                .ToList();
        }

        foreach (WorkspaceDescriptorDto configured in _connectionSettingsService.GetConfiguredWorkspaces())
        {
            UpsertDescriptor(known, configured, requireAccessiblePath: false);
        }

        if (_connectionSettingsService.Current.IncludeLocalWorkspaceCatalog)
        {
            foreach (string fallback in FallbackWorkspaceRoots())
            {
                UpsertDescriptor(known, BuildDescriptor(fallback, null));
            }
        }

        return known
            .OrderByDescending(item => item.LastUsedAt ?? DateTimeOffset.MinValue)
            .ThenBy(item => item.DisplayName, StringComparer.OrdinalIgnoreCase)
            .ToList();
    }

    public async Task RegisterWorkspaceAsync(string workspaceRoot, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(workspaceRoot))
        {
            return;
        }

        DesktopStoragePaths.EnsureCreated();
        List<WorkspaceDescriptorDto> known = await LoadAsync(cancellationToken);
        UpsertDescriptor(
            known,
            BuildDescriptor(workspaceRoot, DateTimeOffset.UtcNow),
            requireAccessiblePath: !IsConfiguredRemotePath(workspaceRoot));
        string json = JsonSerializer.Serialize(known, DesktopJson.Default);
        await File.WriteAllTextAsync(DesktopStoragePaths.WorkspaceRegistryFile, json, cancellationToken);
    }

    private static WorkspaceDescriptorDto BuildDescriptor(string workspaceRoot, DateTimeOffset? lastUsedAt)
    {
        bool isGitRepository = Directory.Exists(Path.Combine(workspaceRoot, ".git"));
        bool hasProjectConfig = File.Exists(Path.Combine(workspaceRoot, ".codex", "config.toml"));
        bool hasMcpOverrides = File.Exists(Path.Combine(workspaceRoot, ".mcp.json"))
            || File.Exists(Path.Combine(workspaceRoot, "mcp-config.json"))
            || Directory.Exists(Path.Combine(workspaceRoot, ".mcp"));

        string trimmed = workspaceRoot.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        string displayName = Path.GetFileName(trimmed);
        if (string.IsNullOrWhiteSpace(displayName))
        {
            displayName = workspaceRoot;
        }

        return new WorkspaceDescriptorDto(
            displayName,
            workspaceRoot,
            isGitRepository ? workspaceRoot : string.Empty,
            isGitRepository,
            hasProjectConfig,
            hasMcpOverrides,
            "workspace-default",
            lastUsedAt);
    }

    private static IEnumerable<string> FallbackWorkspaceRoots()
    {
        string? repositoryRoot = TryFindRepositoryRoot();
        if (!string.IsNullOrWhiteSpace(repositoryRoot))
        {
            yield return repositoryRoot;
        }

        string currentDirectory = Environment.CurrentDirectory;
        if (Directory.Exists(currentDirectory)
            && !string.Equals(currentDirectory, repositoryRoot, StringComparison.OrdinalIgnoreCase))
        {
            yield return currentDirectory;
        }

        string userProfile = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        string[] candidates =
        {
            Path.Combine(userProfile, "source"),
            Path.Combine(userProfile, "Documents", "GitHub"),
            Path.Combine(userProfile, "Documents", "Source")
        };

        foreach (string candidate in candidates.Where(Directory.Exists))
        {
            yield return candidate;
        }
    }

    private static string? TryFindRepositoryRoot()
    {
        foreach (string start in new[] { AppContext.BaseDirectory, Environment.CurrentDirectory })
        {
            DirectoryInfo? current = new(start);
            while (current != null)
            {
                bool hasGitMarker = Directory.Exists(Path.Combine(current.FullName, ".git"))
                    || File.Exists(Path.Combine(current.FullName, ".git"));
                bool hasProjectMarker = File.Exists(Path.Combine(current.FullName, "pom.xml"))
                    || Directory.GetFiles(current.FullName, "*.sln").Length > 0;
                if (hasGitMarker && hasProjectMarker)
                {
                    return current.FullName;
                }

                current = current.Parent;
            }
        }

        return null;
    }

    private static async Task<List<WorkspaceDescriptorDto>> LoadAsync(CancellationToken cancellationToken)
    {
        if (!File.Exists(DesktopStoragePaths.WorkspaceRegistryFile))
        {
            return new List<WorkspaceDescriptorDto>();
        }

        string json = await File.ReadAllTextAsync(DesktopStoragePaths.WorkspaceRegistryFile, cancellationToken);
        return JsonSerializer.Deserialize<List<WorkspaceDescriptorDto>>(json, DesktopJson.Default)
            ?? new List<WorkspaceDescriptorDto>();
    }

    private bool IsConfiguredRemotePath(string workspaceRoot)
    {
        string remotePrefix = _connectionSettingsService.Current.RemotePathPrefix;
        return _connectionSettingsService.Current.ConnectionMode != DesktopConnectionModes.Local
            && !string.IsNullOrWhiteSpace(remotePrefix)
            && workspaceRoot.StartsWith(remotePrefix.TrimEnd('/'), StringComparison.OrdinalIgnoreCase);
    }

    private static bool IsUsableLocalWorkspace(string workspaceRoot, string remotePrefix)
    {
        if (string.IsNullOrWhiteSpace(workspaceRoot) || !Directory.Exists(workspaceRoot))
        {
            return false;
        }

        string normalizedPath = workspaceRoot.Replace('\\', '/');
        string normalizedPrefix = remotePrefix.Replace('\\', '/').TrimEnd('/');
        return string.IsNullOrWhiteSpace(normalizedPrefix)
            || !normalizedPath.StartsWith(normalizedPrefix, StringComparison.OrdinalIgnoreCase);
    }

    private static void UpsertDescriptor(
        List<WorkspaceDescriptorDto> known,
        WorkspaceDescriptorDto descriptor,
        bool requireAccessiblePath = true)
    {
        if (requireAccessiblePath && !Directory.Exists(descriptor.WorkspaceRoot))
        {
            return;
        }

        int index = known.FindIndex(item =>
            string.Equals(item.WorkspaceRoot, descriptor.WorkspaceRoot, StringComparison.OrdinalIgnoreCase));
        if (index >= 0)
        {
            WorkspaceDescriptorDto existing = known[index];
            known[index] = descriptor with
            {
                LastUsedAt = descriptor.LastUsedAt ?? existing.LastUsedAt
            };
            return;
        }

        known.Add(descriptor);
    }
}
