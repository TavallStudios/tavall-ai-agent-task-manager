using AgentTaskManager.Desktop.Contracts;
using System.Diagnostics;

namespace AgentTaskManager.Desktop.Services;

public sealed class RepoLaunchService : IRepoLaunchService
{
    private readonly IDesktopConnectionSettingsService _connectionSettingsService;

    public RepoLaunchService(IDesktopConnectionSettingsService connectionSettingsService)
    {
        _connectionSettingsService = connectionSettingsService;
    }

    public Task LaunchRepoAsync(string workspaceRoot, CancellationToken cancellationToken)
    {
        Process.Start(new ProcessStartInfo
        {
            FileName = ResolveWorkspaceRoot(workspaceRoot),
            UseShellExecute = true
        });
        return Task.CompletedTask;
    }

    public Task OpenFileAsync(string workspaceRoot, FileFocusRequestDto request, CancellationToken cancellationToken)
    {
        string mappedWorkspaceRoot = ResolveWorkspaceRoot(workspaceRoot);
        string filePath = ResolveFilePath(mappedWorkspaceRoot, request.Path);
        Process.Start(new ProcessStartInfo
        {
            FileName = filePath,
            UseShellExecute = true
        });
        return Task.CompletedTask;
    }

    private string ResolveWorkspaceRoot(string workspaceRoot)
    {
        string mapped = _connectionSettingsService.MapWorkspacePathToLocal(workspaceRoot);
        if (Directory.Exists(mapped))
        {
            return mapped;
        }

        if (Directory.Exists(workspaceRoot))
        {
            return workspaceRoot;
        }

        throw new DirectoryNotFoundException(
            $"Workspace '{workspaceRoot}' is not available locally. Update the remote path mapping or mount the remote workspace.");
    }

    private string ResolveFilePath(string mappedWorkspaceRoot, string requestPath)
    {
        string candidate = _connectionSettingsService.MapWorkspacePathToLocal(requestPath);
        if (Path.IsPathRooted(candidate))
        {
            return EnsurePathExists(candidate, requestPath);
        }

        string relativePath = requestPath.Replace('/', Path.DirectorySeparatorChar);
        string combined = Path.Combine(mappedWorkspaceRoot, relativePath);
        return EnsurePathExists(combined, requestPath);
    }

    private static string EnsurePathExists(string candidate, string originalPath)
    {
        if (File.Exists(candidate) || Directory.Exists(candidate))
        {
            return candidate;
        }

        throw new FileNotFoundException(
            $"Path '{originalPath}' is not available locally. Check the remote workspace mapping before opening files.",
            candidate);
    }
}
