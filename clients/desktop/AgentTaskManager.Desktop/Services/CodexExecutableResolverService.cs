namespace AgentTaskManager.Desktop.Services;

public sealed class CodexExecutableResolverService : ICodexExecutableResolverService
{
    public Task<string> ResolveExecutablePathAsync(CancellationToken cancellationToken)
        => ResolveExecutablePathAsync(preferredPath: null, cancellationToken);

    public Task<string> ResolveExecutablePathAsync(string? preferredPath, CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        if (!string.IsNullOrWhiteSpace(preferredPath))
        {
            string normalized = preferredPath.Trim();
            if (File.Exists(normalized))
            {
                return Task.FromResult(normalized);
            }

            throw new FileNotFoundException($"The configured Codex executable was not found at '{normalized}'.");
        }

        foreach (string candidate in CandidatePaths())
        {
            if (File.Exists(candidate))
            {
                return Task.FromResult(candidate);
            }
        }

        throw new FileNotFoundException(
            "Unable to locate the installed Codex executable. Expected the OpenAI npm package to provide codex.cmd or codex.exe.");
    }

    private static IEnumerable<string> CandidatePaths()
    {
        string roamingAppData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        string[] common =
        {
            Path.Combine(roamingAppData, "npm", "codex.cmd"),
            Path.Combine(roamingAppData, "npm", "codex.ps1"),
            Path.Combine(
                roamingAppData,
                "npm",
                "node_modules",
                "@openai",
                "codex",
                "node_modules",
                "@openai",
                "codex-win32-x64",
                "vendor",
                "x86_64-pc-windows-msvc",
                "codex",
                "codex.exe"),
            Path.Combine(localAppData, "Programs", "OpenAI", "Codex", "codex.exe")
        };

        foreach (string item in common)
        {
            yield return item;
        }

        string? path = Environment.GetEnvironmentVariable("PATH");
        if (string.IsNullOrWhiteSpace(path))
        {
            yield break;
        }

        foreach (string directory in path.Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries))
        {
            yield return Path.Combine(directory.Trim(), "codex.cmd");
            yield return Path.Combine(directory.Trim(), "codex.exe");
        }
    }
}
