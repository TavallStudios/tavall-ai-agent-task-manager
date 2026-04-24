using AgentTaskManager.Desktop.Contracts;
using System.Diagnostics;

namespace AgentTaskManager.Desktop.Services;

public sealed class DiffNavigationService : IDiffNavigationService
{
    public async Task OpenDiffAsync(PatchArtifactDto patch, CancellationToken cancellationToken)
    {
        DesktopStoragePaths.EnsureCreated();
        string diffFileName = $"{patch.PatchId}.diff";
        string fullPath = Path.Combine(DesktopStoragePaths.DiffPreviewDirectory, diffFileName);
        await File.WriteAllTextAsync(fullPath, patch.DiffPreview, cancellationToken);
        Process.Start(new ProcessStartInfo
        {
            FileName = fullPath,
            UseShellExecute = true
        });
    }
}
