using AgentTaskManager.Desktop.Contracts;

namespace AgentTaskManager.Desktop.Services;

public sealed class CodexSupervisorService : ICodexSupervisorService
{
    private readonly ICodexWorkspaceConfigurationService _codexWorkspaceConfigurationService;

    public CodexSupervisorService(ICodexWorkspaceConfigurationService codexWorkspaceConfigurationService)
    {
        _codexWorkspaceConfigurationService = codexWorkspaceConfigurationService;
    }

    public async Task<RuntimeLaunchEnvelopeDto> BuildLaunchEnvelopeAsync(
        SessionDetailDto session,
        CancellationToken cancellationToken)
        => await _codexWorkspaceConfigurationService.BuildLaunchEnvelopeAsync(session, cancellationToken);

    public string BuildRuntimeBanner(SessionDetailDto session)
    {
        RuntimeConnectionDto runtime = session.RuntimeConnection;
        RuntimeLeaseDto lease = session.RuntimeLease;
        string transport = string.IsNullOrWhiteSpace(runtime.TransportKind) ? "unknown transport" : runtime.TransportKind;
        return $"Runtime {runtime.ConnectionState}. Lease {lease.LeaseState}. {transport} using {runtime.PreferredModel}.";
    }
}
