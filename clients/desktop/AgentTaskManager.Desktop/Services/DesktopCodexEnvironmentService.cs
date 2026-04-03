using AgentTaskManager.Desktop.Contracts;
using System.Diagnostics;
using System.Text.Json;

namespace AgentTaskManager.Desktop.Services;

public sealed class DesktopCodexEnvironmentService : ICodexEnvironmentService
{
    private readonly ICodexExecutableResolverService _codexExecutableResolverService;

    public DesktopCodexEnvironmentService(ICodexExecutableResolverService codexExecutableResolverService)
    {
        _codexExecutableResolverService = codexExecutableResolverService;
    }

    public async Task<CodexLocalSetupDto> ResolveSetupAsync(
        string? preferredExecutablePath,
        string? preferredCodexHomePath,
        CancellationToken cancellationToken)
    {
        string executablePath = await _codexExecutableResolverService.ResolveExecutablePathAsync(
            preferredExecutablePath,
            cancellationToken);
        string codexHomePath = ResolveCodexHome(preferredCodexHomePath);
        string configFilePath = Path.Combine(codexHomePath, "config.toml");
        string authFilePath = Path.Combine(codexHomePath, "auth.json");
        string authMode = await ReadAuthModeAsync(authFilePath, cancellationToken);
        string loginStatus = await ReadLoginStatusAsync(executablePath, codexHomePath, cancellationToken);
        bool isAuthenticated = loginStatus.StartsWith("Logged in", StringComparison.OrdinalIgnoreCase);
        bool usesChatGpt = authMode.Equals("chatgpt", StringComparison.OrdinalIgnoreCase)
            || loginStatus.Contains("ChatGPT", StringComparison.OrdinalIgnoreCase);

        return new CodexLocalSetupDto(
            executablePath,
            codexHomePath,
            configFilePath,
            authFilePath,
            string.IsNullOrWhiteSpace(authMode) ? "unknown" : authMode,
            string.IsNullOrWhiteSpace(loginStatus) ? "Codex login status unavailable." : loginStatus,
            isAuthenticated,
            usesChatGpt,
            BuildSummary(codexHomePath, authMode, loginStatus, usesChatGpt));
    }

    public async Task LaunchChatGptLoginAsync(
        string? preferredExecutablePath,
        string? preferredCodexHomePath,
        CancellationToken cancellationToken)
    {
        CodexLocalSetupDto setup = await ResolveSetupAsync(preferredExecutablePath, preferredCodexHomePath, cancellationToken);
        if (setup.UsesChatGpt && setup.IsAuthenticated)
        {
            return;
        }

        var environment = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            ["CODEX_HOME"] = setup.CodexHomePath
        };
        ProcessStartInfo startInfo = CodexProcessStartInfoFactory.Build(
            setup.ExecutablePath,
            setup.CodexHomePath,
            new[] { "login" },
            environment,
            createNoWindow: false,
            redirectOutput: false);
        _ = Process.Start(startInfo)
            ?? throw new InvalidOperationException("Unable to launch the Codex login flow.");
    }

    private static string ResolveCodexHome(string? preferredCodexHomePath)
    {
        if (!string.IsNullOrWhiteSpace(preferredCodexHomePath))
        {
            string explicitPath = preferredCodexHomePath.Trim();
            if (Directory.Exists(explicitPath))
            {
                return explicitPath;
            }

            throw new DirectoryNotFoundException($"The configured Codex home was not found at '{explicitPath}'.");
        }

        string? configured = Environment.GetEnvironmentVariable("CODEX_HOME");
        if (!string.IsNullOrWhiteSpace(configured) && Directory.Exists(configured))
        {
            return configured.Trim();
        }

        string defaultHome = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
            ".codex");
        if (Directory.Exists(defaultHome))
        {
            return defaultHome;
        }

        Directory.CreateDirectory(defaultHome);
        return defaultHome;
    }

    private static async Task<string> ReadAuthModeAsync(string authFilePath, CancellationToken cancellationToken)
    {
        if (!File.Exists(authFilePath))
        {
            return "signed-out";
        }

        string json = await File.ReadAllTextAsync(authFilePath, cancellationToken);
        if (string.IsNullOrWhiteSpace(json))
        {
            return "signed-out";
        }

        using JsonDocument document = JsonDocument.Parse(json);
        if (document.RootElement.TryGetProperty("auth_mode", out JsonElement authMode)
            && authMode.ValueKind == JsonValueKind.String)
        {
            return authMode.GetString() ?? "unknown";
        }

        return document.RootElement.TryGetProperty("OPENAI_API_KEY", out _)
            ? "apiKey"
            : "unknown";
    }

    private static async Task<string> ReadLoginStatusAsync(
        string executablePath,
        string codexHomePath,
        CancellationToken cancellationToken)
    {
        var environment = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            ["CODEX_HOME"] = codexHomePath
        };
        ProcessStartInfo startInfo = CodexProcessStartInfoFactory.Build(
            executablePath,
            codexHomePath,
            new[] { "login", "status" },
            environment,
            createNoWindow: true,
            redirectOutput: true);
        using var process = Process.Start(startInfo)
            ?? throw new InvalidOperationException("Unable to start Codex to read login status.");
        string stdout = await process.StandardOutput.ReadToEndAsync(cancellationToken);
        string stderr = await process.StandardError.ReadToEndAsync(cancellationToken);
        await process.WaitForExitAsync(cancellationToken);
        if (process.ExitCode == 0 && !string.IsNullOrWhiteSpace(stdout))
        {
            return stdout.Trim();
        }

        if (!string.IsNullOrWhiteSpace(stderr))
        {
            return stderr.Trim();
        }

        return string.IsNullOrWhiteSpace(stdout)
            ? "Codex login status unavailable."
            : stdout.Trim();
    }

    private static string BuildSummary(
        string codexHomePath,
        string authMode,
        string loginStatus,
        bool usesChatGpt)
    {
        if (usesChatGpt)
        {
            return $"Using ChatGPT-backed Codex auth from {codexHomePath}.";
        }

        if (authMode.Equals("apiKey", StringComparison.OrdinalIgnoreCase))
        {
            return $"Using API-key Codex auth from {codexHomePath}.";
        }

        return $"{loginStatus} Home: {codexHomePath}.";
    }
}
