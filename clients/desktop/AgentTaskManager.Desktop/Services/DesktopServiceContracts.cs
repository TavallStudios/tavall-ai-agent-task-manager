using AgentTaskManager.Desktop.Contracts;
using System.Net.Http;

namespace AgentTaskManager.Desktop.Services;

public interface ISecureCredentialStorageService
{
    Task StoreSecretAsync(string resource, string userName, string secret, CancellationToken cancellationToken);
    Task<string?> ReadSecretAsync(string resource, string userName, CancellationToken cancellationToken);
    Task RemoveSecretAsync(string resource, string userName, CancellationToken cancellationToken);
}

public interface IBackendAuthService
{
    BackendAuthSessionDto? CurrentSession { get; }
    Uri GetBackendBaseUri();
    Task<BackendAuthSessionDto?> TryRestoreAsync(CancellationToken cancellationToken);
    Task<BackendAuthSessionDto> SignInAsync(Uri backendBaseUri, string userName, string password, CancellationToken cancellationToken);
    Task SignOutAsync(CancellationToken cancellationToken);
    void ApplyAuthentication(HttpRequestMessage message);
}

public interface IDesktopConnectionSettingsService
{
    DesktopConnectionSettingsDto Current { get; }
    Task SaveAsync(DesktopConnectionSettingsDto settings, CancellationToken cancellationToken);
    Task ApplyDetectedRemoteDefaultsAsync(CancellationToken cancellationToken);
    Uri GetBackendBaseUri();
    Task EnsureBackendTransportAsync(CancellationToken cancellationToken);
    Task StopBackendTransportAsync(CancellationToken cancellationToken);
    string GetTransportStatus();
    string MapWorkspacePathToLocal(string workspacePath);
    IReadOnlyList<WorkspaceDescriptorDto> GetConfiguredWorkspaces();
    bool ManageLocalRuntime();
    bool ObserveOnlySessions();
    bool AllowRuntimeHandoff();
    bool CreateRuntimeByDefault();
    int GetSessionListLimit();
    int GetEventReplayLimit();
}

public interface IDevicePresenceService
{
    Task<string> GetOrCreateDeviceIdAsync(CancellationToken cancellationToken);
    Task<string> GetDeviceNameAsync(CancellationToken cancellationToken);
}

public interface IWorkspaceRegistryService
{
    Task<IReadOnlyList<WorkspaceDescriptorDto>> ListWorkspaceRootsAsync(CancellationToken cancellationToken);
    Task RegisterWorkspaceAsync(string workspaceRoot, CancellationToken cancellationToken);
}

public interface ISessionClientService
{
    Task<IReadOnlyList<SessionSummaryDto>> ListSessionsAsync(CancellationToken cancellationToken);
    Task<SessionDetailDto> GetSessionAsync(string sessionId, CancellationToken cancellationToken);
    Task<SessionDetailDto> CreateSessionAsync(CreateSessionRequestDto request, CancellationToken cancellationToken);
    Task<SessionDetailDto> AttachSessionAsync(string sessionId, AttachSessionRequestDto request, CancellationToken cancellationToken);
    Task<SessionDetailDto> ResumeSessionAsync(string sessionId, ResumeSessionRequestDto request, CancellationToken cancellationToken);
    Task<SessionDetailDto> SubmitTurnAsync(string sessionId, SubmitTurnRequestDto request, CancellationToken cancellationToken);
    Task<IReadOnlyList<SessionEventEnvelopeDto>> ListEventsAsync(
        string sessionId,
        string? afterEventId,
        int limit,
        CancellationToken cancellationToken);
}

public interface ISessionStreamService
{
    IAsyncEnumerable<SessionEventEnvelopeDto> StreamEventsAsync(
        string sessionId,
        string? afterEventId,
        int replayLimit,
        CancellationToken cancellationToken);
}

public interface IRuntimeSessionClientService
{
    Task MarkConnectedAsync(string sessionId, RuntimeConnectedRequestDto request, CancellationToken cancellationToken);
    Task MarkDisconnectedAsync(string sessionId, RuntimeDisconnectedRequestDto request, CancellationToken cancellationToken);
    Task PublishEventAsync(string sessionId, RuntimeEventPublishRequestDto request, CancellationToken cancellationToken);
}

public interface ICodexExecutableResolverService
{
    Task<string> ResolveExecutablePathAsync(CancellationToken cancellationToken);
    Task<string> ResolveExecutablePathAsync(string? preferredPath, CancellationToken cancellationToken);
}

public interface ICodexWorkspaceConfigurationService
{
    Task<RuntimeLaunchEnvelopeDto> BuildLaunchEnvelopeAsync(SessionDetailDto session, CancellationToken cancellationToken);
}

public interface ICodexEnvironmentService
{
    Task<CodexLocalSetupDto> ResolveSetupAsync(
        string? preferredExecutablePath,
        string? preferredCodexHomePath,
        CancellationToken cancellationToken);

    Task LaunchChatGptLoginAsync(
        string? preferredExecutablePath,
        string? preferredCodexHomePath,
        CancellationToken cancellationToken);
}

public interface ICodexRuntimeConnection
{
    Task<RuntimeConnectionTelemetryDto> BuildTelemetryAsync(SessionDetailDto session, CancellationToken cancellationToken);
    Task EnsureConnectedAsync(SessionDetailDto session, CancellationToken cancellationToken);
    Task SendTurnAsync(SessionDetailDto session, string promptText, string? backendTurnId, CancellationToken cancellationToken);
    Task StopAsync(string sessionId, CancellationToken cancellationToken);
    Task StopAllAsync(CancellationToken cancellationToken);
}

public interface ICodexSupervisorService
{
    Task<RuntimeLaunchEnvelopeDto> BuildLaunchEnvelopeAsync(SessionDetailDto session, CancellationToken cancellationToken);
    string BuildRuntimeBanner(SessionDetailDto session);
}

public interface IMemoryContextService
{
    string BuildSummary(SessionDetailDto session);
    IReadOnlyList<MemoryContextReferenceDto> OrderEntries(SessionDetailDto session);
}

public interface IToolReceiptService
{
    string BuildSummary(SessionDetailDto session);
    IReadOnlyList<ToolReceiptDto> OrderReceipts(SessionDetailDto session);
}

public interface IVerifierStatusService
{
    string BuildSummary(SessionDetailDto session);
    IReadOnlyList<VerifierResultDto> OrderResults(SessionDetailDto session);
}

public interface IOutputReleaseService
{
    string BuildSummary(SessionDetailDto session);
    OutputSnapshotDto? GetLatestCandidate(SessionDetailDto session);
    OutputSnapshotDto? GetLatestApproved(SessionDetailDto session);
}

public interface IRepoLaunchService
{
    Task LaunchRepoAsync(string workspaceRoot, CancellationToken cancellationToken);
    Task OpenFileAsync(string workspaceRoot, FileFocusRequestDto request, CancellationToken cancellationToken);
}

public interface IDiffNavigationService
{
    Task OpenDiffAsync(PatchArtifactDto patch, CancellationToken cancellationToken);
}

public interface IRemoteSessionResumeService
{
    Task<SessionDetailDto> ResumeAsync(string sessionId, CancellationToken cancellationToken);
}
