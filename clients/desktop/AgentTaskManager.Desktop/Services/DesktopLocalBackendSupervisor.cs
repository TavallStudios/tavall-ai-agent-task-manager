using AgentTaskManager.Desktop.Contracts;
using System.Diagnostics;
using System.Net.Sockets;

namespace AgentTaskManager.Desktop.Services;

public sealed class DesktopLocalBackendSupervisor
{
    private readonly WindowsKillOnCloseJob _job = new();
    private Process? _managedBackendProcess;

    public async Task EnsureBackendAsync(
        DesktopConnectionSettingsDto settings,
        CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        if (settings.ConnectionMode != DesktopConnectionModes.Local)
        {
            return;
        }

        int port = ResolveLocalPort(settings);
        if (await IsPortOpenAsync(port, cancellationToken))
        {
            return;
        }

        await StopAsync(cancellationToken);
        _managedBackendProcess = StartBackendProcess(port);
        DesktopDiagnosticsLog.WriteInfo($"Started managed local backend on port {port}.");
        await WaitForBackendAsync(port, settings.TunnelConnectTimeoutSeconds, cancellationToken);
    }

    public Task StopAsync(CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        if (_managedBackendProcess == null)
        {
            return Task.CompletedTask;
        }

        try
        {
            if (!_managedBackendProcess.HasExited)
            {
                _managedBackendProcess.Kill(entireProcessTree: true);
                _managedBackendProcess.WaitForExit(5000);
            }
        }
        catch
        {
        }
        finally
        {
            DesktopDiagnosticsLog.WriteInfo("Stopped managed local backend.");
            _managedBackendProcess.Dispose();
            _managedBackendProcess = null;
        }

        return Task.CompletedTask;
    }

    public string BuildStatusSummary(DesktopConnectionSettingsDto settings)
    {
        int port = ResolveLocalPort(settings);
        string endpoint = $"http://127.0.0.1:{port}";
        if (_managedBackendProcess is { HasExited: false })
        {
            return $"Managed local backend active at {endpoint}.";
        }

        return IsPortOpen(port)
            ? $"Local backend active at {endpoint}."
            : $"Local backend configured at {endpoint}.";
    }

    private Process StartBackendProcess(int port)
    {
        string repoRoot = ResolveRepoRoot();
        string scriptPath = Path.Combine(repoRoot, "scripts", "start-local-backend.cmd");
        if (!File.Exists(scriptPath))
        {
            throw new FileNotFoundException($"Local backend launcher not found at '{scriptPath}'.");
        }

        ProcessStartInfo startInfo = CodexProcessStartInfoFactory.Build(
            scriptPath,
            repoRoot,
            ["-Port", port.ToString()],
            new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase),
            createNoWindow: true,
            redirectOutput: false);
        Process process = Process.Start(startInfo)
            ?? throw new InvalidOperationException($"Unable to start local backend launcher from '{scriptPath}'.");
        _job.AssignProcess(process);
        return process;
    }

    private static async Task WaitForBackendAsync(int port, int timeoutSeconds, CancellationToken cancellationToken)
    {
        DateTimeOffset deadline = DateTimeOffset.UtcNow.AddSeconds(Math.Max(5, timeoutSeconds));
        while (DateTimeOffset.UtcNow < deadline)
        {
            cancellationToken.ThrowIfCancellationRequested();
            if (await IsPortOpenAsync(port, cancellationToken))
            {
                return;
            }

            await Task.Delay(250, cancellationToken);
        }

        throw new TimeoutException($"Timed out waiting for the local backend on 127.0.0.1:{port}.");
    }

    private static int ResolveLocalPort(DesktopConnectionSettingsDto settings)
    {
        if (Uri.TryCreate(settings.DirectBackendBaseUrl, UriKind.Absolute, out Uri? uri)
            && uri.Port > 0)
        {
            return uri.Port;
        }

        return 9000;
    }

    private static string ResolveRepoRoot()
    {
        DirectoryInfo? current = new(AppContext.BaseDirectory);
        while (current != null)
        {
            bool hasLauncher = File.Exists(Path.Combine(current.FullName, "scripts", "start-local-backend.cmd"));
            bool looksLikeRepoRoot = File.Exists(Path.Combine(current.FullName, "pom.xml"));
            if (hasLauncher && looksLikeRepoRoot)
            {
                return current.FullName;
            }

            current = current.Parent;
        }

        throw new DirectoryNotFoundException("Unable to locate the AgentTaskManager repo root for local backend startup.");
    }

    private static bool IsPortOpen(int port)
    {
        try
        {
            using var client = new TcpClient();
            IAsyncResult result = client.BeginConnect("127.0.0.1", port, null, null);
            return result.AsyncWaitHandle.WaitOne(200) && client.Connected;
        }
        catch
        {
            return false;
        }
    }

    private static async Task<bool> IsPortOpenAsync(int port, CancellationToken cancellationToken)
    {
        try
        {
            using var client = new TcpClient();
            await client.ConnectAsync("127.0.0.1", port, cancellationToken);
            return true;
        }
        catch
        {
            return false;
        }
    }
}
