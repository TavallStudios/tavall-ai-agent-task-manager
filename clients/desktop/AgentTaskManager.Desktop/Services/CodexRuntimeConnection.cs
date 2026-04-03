using AgentTaskManager.Desktop.Contracts;
using System.Collections.Concurrent;

namespace AgentTaskManager.Desktop.Services;

public sealed class CodexRuntimeConnection : ICodexRuntimeConnection
{
    private readonly ICodexSupervisorService _codexSupervisorService;
    private readonly IRuntimeSessionClientService _runtimeSessionClientService;
    private readonly ConcurrentDictionary<string, CodexAppServerSessionHost> _hosts = new(StringComparer.OrdinalIgnoreCase);

    public CodexRuntimeConnection(
        ICodexSupervisorService codexSupervisorService,
        IRuntimeSessionClientService runtimeSessionClientService)
    {
        _codexSupervisorService = codexSupervisorService;
        _runtimeSessionClientService = runtimeSessionClientService;
    }

    public Task<RuntimeConnectionTelemetryDto> BuildTelemetryAsync(
        SessionDetailDto session,
        CancellationToken cancellationToken)
    {
        RuntimeConnectionDto runtime = session.RuntimeConnection;
        string detail = $"Transport {runtime.TransportKind}, auth {runtime.AuthMode}, endpoint {runtime.EndpointDescription}.";
        return Task.FromResult(new RuntimeConnectionTelemetryDto(
            runtime.RuntimeId,
            $"Runtime {runtime.ConnectionState}",
            detail,
            runtime.LastHeartbeatAt,
            runtime.ConnectionState,
            runtime.AuthMode));
    }

    public async Task EnsureConnectedAsync(SessionDetailDto session, CancellationToken cancellationToken)
    {
        CodexAppServerSessionHost host = await GetOrCreateHostAsync(session, cancellationToken);
        await host.EnsureConnectedAsync(session, cancellationToken);
    }

    public async Task SendTurnAsync(
        SessionDetailDto session,
        string promptText,
        string? backendTurnId,
        CancellationToken cancellationToken)
    {
        CodexAppServerSessionHost host = await GetOrCreateHostAsync(session, cancellationToken);
        await host.SendTurnAsync(session, promptText, backendTurnId, cancellationToken);
    }

    public async Task StopAsync(string sessionId, CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        if (!_hosts.TryRemove(sessionId, out CodexAppServerSessionHost? host))
        {
            return;
        }

        await host.StopAsync("Stopped by desktop client.", true, cancellationToken);
    }

    public async Task StopAllAsync(CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        foreach ((string sessionId, CodexAppServerSessionHost host) in _hosts.ToArray())
        {
            _hosts.TryRemove(sessionId, out _);
            await host.StopAsync("Stopped by desktop client.", true, cancellationToken);
        }
    }

    private async Task<CodexAppServerSessionHost> GetOrCreateHostAsync(SessionDetailDto session, CancellationToken cancellationToken)
    {
        if (_hosts.TryGetValue(session.Summary.SessionId, out CodexAppServerSessionHost? existing))
        {
            return existing;
        }

        RuntimeLaunchEnvelopeDto envelope = await _codexSupervisorService.BuildLaunchEnvelopeAsync(session, cancellationToken);
        var created = new CodexAppServerSessionHost(envelope, _runtimeSessionClientService);
        return _hosts.GetOrAdd(session.Summary.SessionId, created);
    }
}
