using AgentTaskManager.Desktop.Contracts;
using System.Diagnostics;
using System.Net.Sockets;

namespace AgentTaskManager.Desktop.Services;

public sealed class DesktopSshTunnelSupervisor
{
    private readonly WindowsKillOnCloseJob _job = new();
    private Process? _managedTunnelProcess;

    public async Task EnsureBackendTunnelAsync(
        DesktopConnectionSettingsDto settings,
        CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        if (settings.ConnectionMode != DesktopConnectionModes.RemoteTunnel)
        {
            return;
        }

        if (await IsPortOpenAsync(settings.LocalTunnelPort, cancellationToken))
        {
            return;
        }

        await StopAsync(cancellationToken);
        _managedTunnelProcess = StartTunnelProcess(settings);
        await WaitForTunnelAsync(settings, cancellationToken);
    }

    public Task StopAsync(CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        if (_managedTunnelProcess == null)
        {
            return Task.CompletedTask;
        }

        try
        {
            if (!_managedTunnelProcess.HasExited)
            {
                _managedTunnelProcess.Kill(entireProcessTree: true);
                _managedTunnelProcess.WaitForExit(5000);
            }
        }
        catch
        {
        }
        finally
        {
            _managedTunnelProcess.Dispose();
            _managedTunnelProcess = null;
        }

        return Task.CompletedTask;
    }

    public string BuildStatusSummary(DesktopConnectionSettingsDto settings)
    {
        if (settings.ConnectionMode == DesktopConnectionModes.Local)
        {
            return $"Local backend {settings.DirectBackendBaseUrl}.";
        }

        if (settings.ConnectionMode == DesktopConnectionModes.RemoteDirect)
        {
            return $"Direct remote backend {settings.DirectBackendBaseUrl}.";
        }

        string endpoint = $"http://127.0.0.1:{settings.LocalTunnelPort}";
        if (_managedTunnelProcess is { HasExited: false })
        {
            return $"Managed tunnel active at {endpoint} via {settings.RemoteUser}@{settings.RemoteHost}.";
        }

        return $"Remote tunnel endpoint {endpoint} via {settings.RemoteUser}@{settings.RemoteHost}.";
    }

    private Process StartTunnelProcess(DesktopConnectionSettingsDto settings)
    {
        string sshExecutable = ResolveSshExecutablePath();
        string destination = $"{settings.RemoteUser}@{settings.RemoteHost}";
        string arguments =
            $"-N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -p {settings.RemoteSshPort} " +
            $"-L {settings.LocalTunnelPort}:127.0.0.1:{settings.RemoteBackendPort} -i \"{settings.SshKeyPath}\" {destination}";

        var process = new Process
        {
            StartInfo = new ProcessStartInfo
            {
                FileName = sshExecutable,
                Arguments = arguments,
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardError = true,
                RedirectStandardOutput = true
            }
        };

        process.Start();
        _job.AssignProcess(process);
        return process;
    }

    private static async Task WaitForTunnelAsync(
        DesktopConnectionSettingsDto settings,
        CancellationToken cancellationToken)
    {
        DateTimeOffset deadline = DateTimeOffset.UtcNow.AddSeconds(settings.TunnelConnectTimeoutSeconds);
        while (DateTimeOffset.UtcNow < deadline)
        {
            cancellationToken.ThrowIfCancellationRequested();
            if (await IsPortOpenAsync(settings.LocalTunnelPort, cancellationToken))
            {
                return;
            }

            await Task.Delay(250, cancellationToken);
        }

        throw new TimeoutException(
            $"Timed out waiting for SSH tunnel on 127.0.0.1:{settings.LocalTunnelPort}.");
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

    private static string ResolveSshExecutablePath()
    {
        string windowsOpenSsh = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.Windows),
            "System32",
            "OpenSSH",
            "ssh.exe");
        return File.Exists(windowsOpenSsh) ? windowsOpenSsh : "ssh";
    }
}
