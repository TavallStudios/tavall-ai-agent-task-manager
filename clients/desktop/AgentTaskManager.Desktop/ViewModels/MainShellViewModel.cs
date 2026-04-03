using AgentTaskManager.Desktop.Contracts;
using AgentTaskManager.Desktop.Services;
using AgentTaskManager.Desktop.Utility;
using Microsoft.UI.Dispatching;

namespace AgentTaskManager.Desktop.ViewModels;

public sealed class MainShellViewModel
{
    private readonly DispatcherQueue _dispatcherQueue;
    private readonly IBackendAuthService _backendAuthService;
    private readonly IDesktopConnectionSettingsService _connectionSettingsService;
    private readonly IWorkspaceRegistryService _workspaceRegistryService;
    private readonly ISessionClientService _sessionClientService;
    private readonly ISessionStreamService _sessionStreamService;
    private readonly ICodexRuntimeConnection _codexRuntimeConnection;
    private readonly IRemoteSessionResumeService _remoteSessionResumeService;
    private readonly IRepoLaunchService _repoLaunchService;
    private readonly IDiffNavigationService _diffNavigationService;
    private readonly IDevicePresenceService _devicePresenceService;
    private CancellationTokenSource? _streamCancellationTokenSource;
    private bool _initialized;
    private bool _isLoadingSelectedSession;

    public MainShellViewModel(
        IBackendAuthService backendAuthService,
        IDesktopConnectionSettingsService connectionSettingsService,
        IWorkspaceRegistryService workspaceRegistryService,
        ISessionClientService sessionClientService,
        ISessionStreamService sessionStreamService,
        ICodexRuntimeConnection codexRuntimeConnection,
        IRemoteSessionResumeService remoteSessionResumeService,
        IRepoLaunchService repoLaunchService,
        IDiffNavigationService diffNavigationService,
        IDevicePresenceService devicePresenceService,
        ConnectionSettingsViewModel connection,
        CodexSettingsViewModel codex,
        SignInViewModel signIn,
        WorkspacePickerViewModel workspacePicker,
        SessionListViewModel sessionList,
        SessionDetailViewModel sessionDetail,
        StatusStripViewModel statusStrip)
    {
        _dispatcherQueue = DispatcherQueue.GetForCurrentThread()
            ?? throw new InvalidOperationException("MainShellViewModel must be created on the UI thread.");
        _backendAuthService = backendAuthService;
        _connectionSettingsService = connectionSettingsService;
        _workspaceRegistryService = workspaceRegistryService;
        _sessionClientService = sessionClientService;
        _sessionStreamService = sessionStreamService;
        _codexRuntimeConnection = codexRuntimeConnection;
        _remoteSessionResumeService = remoteSessionResumeService;
        _repoLaunchService = repoLaunchService;
        _diffNavigationService = diffNavigationService;
        _devicePresenceService = devicePresenceService;
        Connection = connection;
        Codex = codex;
        SignIn = signIn;
        WorkspacePicker = workspacePicker;
        SessionList = sessionList;
        SessionDetail = sessionDetail;
        StatusStrip = statusStrip;
    }

    public ConnectionSettingsViewModel Connection { get; }
    public CodexSettingsViewModel Codex { get; }
    public SignInViewModel SignIn { get; }
    public WorkspacePickerViewModel WorkspacePicker { get; }
    public SessionListViewModel SessionList { get; }
    public SessionDetailViewModel SessionDetail { get; }
    public StatusStripViewModel StatusStrip { get; }

    public void HandleError(Exception exception)
    {
        SignIn.StatusMessage = exception.Message;
        StatusStrip.SetStreamStatus(exception.Message);
    }

    public async Task InitializeAsync()
    {
        if (_initialized)
        {
            return;
        }

        _initialized = true;
        StatusStrip.ApplySignedOut();
        await Connection.InitializeAsync(CancellationToken.None);
        await Codex.InitializeAsync(CancellationToken.None);
        await ReloadConnectionBindingsAsync();

        BackendAuthSessionDto? restored = await _backendAuthService.TryRestoreAsync(CancellationToken.None);
        if (restored == null)
        {
            SignIn.MarkSignedOut("Sign in to AgentTaskManager.");
            PreserveConfiguredBackendUrl();
            return;
        }

        SignIn.ApplySession(restored);
        await RefreshSessionsAsync();
    }

    public async Task SignInAsync(string password)
    {
        SignIn.IsBusy = true;
        try
        {
            await SaveProfileSettingsAsync();
            await ReloadConnectionBindingsAsync();
            BackendAuthSessionDto session = await _backendAuthService.SignInAsync(
                new Uri(Connection.EffectiveBackendBaseUrl),
                SignIn.UserName,
                password,
                CancellationToken.None);
            SignIn.ApplySession(session);
            await RefreshSessionsAsync();
        }
        finally
        {
            SignIn.IsBusy = false;
        }
    }

    public async Task SignOutAsync()
    {
        StopStreaming();
        await _codexRuntimeConnection.StopAllAsync(CancellationToken.None);
        await _backendAuthService.SignOutAsync(CancellationToken.None);
        await Connection.StopTransportAsync(CancellationToken.None);
        SignIn.MarkSignedOut("Signed out from AgentTaskManager.");
        PreserveConfiguredBackendUrl();
        SessionList.Clear();
        SessionDetail.Clear();
        StatusStrip.ApplySignedOut();
    }

    public async Task SaveConnectionAsync()
    {
        await SaveProfileSettingsAsync();
        await ApplyConnectionChangeAsync(resetTransport: true);
    }

    public async Task DetectRemoteDefaultsAsync()
    {
        await Connection.ApplyDetectedRemoteDefaultsAsync(CancellationToken.None);
        await ApplyConnectionChangeAsync(resetTransport: true);
    }

    public async Task UseLocalModeAsync()
    {
        Connection.UseLocalMode();
        await SaveProfileSettingsAsync();
        await ApplyConnectionChangeAsync(resetTransport: true);
    }

    public async Task UseRemoteModeAsync()
    {
        Connection.UseRemoteMode();
        await SaveProfileSettingsAsync();
        await ApplyConnectionChangeAsync(resetTransport: true);
    }

    public Task UseDetectedCodexSetupAsync()
        => Codex.UseDetectedSetupAsync(CancellationToken.None);

    public Task RefreshCodexStatusAsync()
        => Codex.RefreshStatusAsync(CancellationToken.None);

    public Task StartChatGptCodexLoginAsync()
        => Codex.StartChatGptSignInAsync(CancellationToken.None);

    public async Task ConnectTransportAsync()
    {
        await Connection.EnsureTransportAsync(CancellationToken.None);
        await ApplyConnectionChangeAsync(resetTransport: false);
    }

    public async Task DisconnectTransportAsync()
    {
        await Connection.StopTransportAsync(CancellationToken.None);
        await ApplyConnectionChangeAsync(resetTransport: false);
    }

    public async Task CreateSessionAsync()
    {
        CreateSessionRequestDto request = WorkspacePicker.BuildCreateSessionRequest();
        await _workspaceRegistryService.RegisterWorkspaceAsync(request.WorkspaceRoot, CancellationToken.None);
        SessionDetailDto detail = await _sessionClientService.CreateSessionAsync(request, CancellationToken.None);
        ApplySession(detail);
        await StartStreamingAsync(detail.Summary.SessionId);
        if (request.CreateRuntime)
        {
            await EnsureRuntimeAsync(detail);
        }
    }

    public async Task LoadSelectedSessionAsync()
    {
        SessionSummaryDto? selected = SessionList.SelectedSession;
        if (selected == null)
        {
            return;
        }

        if (_isLoadingSelectedSession)
        {
            return;
        }

        if (string.Equals(SessionDetail.SessionId, selected.SessionId, StringComparison.Ordinal)
            && !string.Equals(SessionDetail.LifecycleState, "CREATED", StringComparison.OrdinalIgnoreCase))
        {
            return;
        }

        _isLoadingSelectedSession = true;
        try
        {
            string deviceId = await _devicePresenceService.GetOrCreateDeviceIdAsync(CancellationToken.None);
            string deviceName = await _devicePresenceService.GetDeviceNameAsync(CancellationToken.None);
            bool observeOnly = !_connectionSettingsService.ManageLocalRuntime()
                || _connectionSettingsService.ObserveOnlySessions();
            SessionDetailDto detail = await _sessionClientService.AttachSessionAsync(
                selected.SessionId,
                new AttachSessionRequestDto(deviceId, deviceName, "DESKTOP", deviceName, observeOnly),
                CancellationToken.None);
            ApplySession(detail);
            await StartStreamingAsync(detail.Summary.SessionId);
        }
        finally
        {
            _isLoadingSelectedSession = false;
        }
    }

    public async Task ResumeSelectedSessionAsync()
    {
        SessionSummaryDto? selected = SessionList.SelectedSession;
        if (selected == null)
        {
            return;
        }

        SessionDetailDto detail = await _remoteSessionResumeService.ResumeAsync(selected.SessionId, CancellationToken.None);
        ApplySession(detail);
        await StartStreamingAsync(detail.Summary.SessionId);
        await EnsureRuntimeAsync(detail);
    }

    public async Task SubmitTurnAsync()
    {
        if (string.IsNullOrWhiteSpace(SessionDetail.SessionId) || string.IsNullOrWhiteSpace(SessionDetail.PendingPrompt))
        {
            return;
        }

        string promptText = SessionDetail.PendingPrompt;
        SessionDetailDto detail = await _sessionClientService.SubmitTurnAsync(
            SessionDetail.SessionId,
            new SubmitTurnRequestDto(promptText, "edit", new[] { "repo-context", "validation", "patch-gate" }, true),
            CancellationToken.None);
        SessionDetail.PendingPrompt = string.Empty;
        ApplySession(detail);
        await EnsureRuntimeAsync(detail);
        if (_connectionSettingsService.ManageLocalRuntime())
        {
            await _codexRuntimeConnection.SendTurnAsync(detail, promptText, detail.Summary.ActiveTurnId, CancellationToken.None);
        }
    }

    public Task OpenSelectedWorkspaceAsync()
        => string.IsNullOrWhiteSpace(SessionDetail.WorkspaceRoot)
            ? Task.CompletedTask
            : _repoLaunchService.LaunchRepoAsync(SessionDetail.WorkspaceRoot, CancellationToken.None);

    public Task OpenSelectedPatchAsync()
        => SessionDetail.PatchReview.SelectedPatch is { } patch
            ? _diffNavigationService.OpenDiffAsync(patch, CancellationToken.None)
            : Task.CompletedTask;

    public Task OpenSelectedFileAsync()
        => SessionDetail.PatchReview.SelectedFileFocusRequest is { } fileRequest
            ? _repoLaunchService.OpenFileAsync(SessionDetail.WorkspaceRoot, fileRequest, CancellationToken.None)
            : Task.CompletedTask;

    public async Task ReloadConnectionBindingsAsync()
    {
        WorkspacePicker.LoadWorkspaces(await _workspaceRegistryService.ListWorkspaceRootsAsync(CancellationToken.None));
        Connection.ApplyTo(SignIn, WorkspacePicker);
        PreserveConfiguredBackendUrl();
        StatusStrip.SetStreamStatus(Connection.TransportStatus);
    }

    public async Task ShutdownAsync()
    {
        StopStreaming();
        await _codexRuntimeConnection.StopAllAsync(CancellationToken.None);
        await Connection.StopTransportAsync(CancellationToken.None);
    }

    private async Task RefreshSessionsAsync()
    {
        SessionList.ReplaceSessions(await _sessionClientService.ListSessionsAsync(CancellationToken.None));
        SignIn.MarkBackendHealthy();
        if (string.IsNullOrWhiteSpace(SessionDetail.SessionId))
        {
            StatusStrip.ApplyConnectedBackend(Connection.TransportStatus);
        }
    }

    private async Task EnsureRuntimeAsync(SessionDetailDto detail)
    {
        if (!_connectionSettingsService.ManageLocalRuntime())
        {
            StatusStrip.SetStreamStatus(_connectionSettingsService.ObserveOnlySessions()
                ? "Observe-only mode is active. Runtime stays on the remote host."
                : "Remote-managed runtime stays on the backend.");
            return;
        }

        string deviceId = await _devicePresenceService.GetOrCreateDeviceIdAsync(CancellationToken.None);
        if (!string.Equals(detail.RuntimeLease.OwnerDeviceId, deviceId, StringComparison.OrdinalIgnoreCase)
            && detail.RuntimeLease.LeaseState != "HANDOFF_PENDING"
            && detail.RuntimeLease.LeaseState != "UNASSIGNED")
        {
            StatusStrip.SetStreamStatus("Observing session without taking the runtime lease.");
            return;
        }

        await Codex.SaveAsync(CancellationToken.None);
        await _codexRuntimeConnection.EnsureConnectedAsync(detail, CancellationToken.None);
        StatusStrip.SetStreamStatus("Local Codex app-server connected.");
    }

    private async Task SaveProfileSettingsAsync()
    {
        await Codex.SaveAsync(CancellationToken.None);
        await Connection.SaveAsync(CancellationToken.None);
    }

    private void ApplySession(SessionDetailDto detail)
    {
        SessionList.Upsert(detail.Summary);
        SessionDetail.ApplySession(detail);
        StatusStrip.ApplySession(detail);
        StatusStrip.SetStreamStatus("Event stream connected.");
        SignIn.MarkBackendHealthy();
    }

    private Task StartStreamingAsync(string sessionId)
    {
        StopStreaming();
        _streamCancellationTokenSource = new CancellationTokenSource();
        CancellationToken cancellationToken = _streamCancellationTokenSource.Token;
        string? lastEventId = SessionDetail.LatestEventId;
        int replayLimit = _connectionSettingsService.GetEventReplayLimit();
        _ = Task.Run(async () =>
        {
            int attempt = 0;
            while (!cancellationToken.IsCancellationRequested)
            {
                try
                {
                    await foreach (SessionEventEnvelopeDto item in _sessionStreamService.StreamEventsAsync(
                                       sessionId,
                                       lastEventId,
                                       replayLimit,
                                       cancellationToken))
                    {
                        lastEventId = item.EventId;
                        await _dispatcherQueue.EnqueueAsync(() =>
                        {
                            SessionDetail.AppendEvent(item);
                            StatusStrip.SetStreamStatus($"Live stream received {item.EventType} at {item.OccurredAt:HH:mm:ss}.");
                        });

                        if (!SessionRefreshPolicy.ShouldRefresh(item.EventType))
                        {
                            continue;
                        }

                        SessionDetailDto refreshed = await _sessionClientService.GetSessionAsync(sessionId, cancellationToken);
                        await _dispatcherQueue.EnqueueAsync(() => ApplySession(refreshed));
                    }

                    await Task.Delay(TimeSpan.FromSeconds(2), cancellationToken);
                }
                catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
                {
                    break;
                }
                catch (Exception exception)
                {
                    attempt++;
                    TimeSpan delay = TimeSpan.FromSeconds(Math.Min(30, Math.Pow(2, Math.Min(attempt, 5))));
                    await _dispatcherQueue.EnqueueAsync(() =>
                        StatusStrip.SetStreamStatus($"Stream reconnecting in {delay.TotalSeconds:0}s. {exception.Message}"));
                    await Task.Delay(delay, cancellationToken);
                }
            }
        }, cancellationToken);
        return Task.CompletedTask;
    }

    private void PreserveConfiguredBackendUrl()
    {
        string configuredBackend = NormalizeBaseUrl(Connection.EffectiveBackendBaseUrl);
        string? currentSessionBackend = _backendAuthService.CurrentSession?.BackendBaseUrl;
        SignIn.BackendUrl = NormalizeBaseUrl(currentSessionBackend) == configuredBackend
            ? currentSessionBackend ?? Connection.EffectiveBackendBaseUrl
            : Connection.EffectiveBackendBaseUrl;
    }

    private async Task ApplyConnectionChangeAsync(bool resetTransport)
    {
        StopStreaming();
        await _codexRuntimeConnection.StopAllAsync(CancellationToken.None);
        if (resetTransport)
        {
            await Connection.StopTransportAsync(CancellationToken.None);
        }

        SessionList.Clear();
        SessionDetail.Clear();
        await ReloadConnectionBindingsAsync();

        BackendAuthSessionDto? restored = await _backendAuthService.TryRestoreAsync(CancellationToken.None);
        if (restored == null)
        {
            SignIn.MarkSignedOut("Sign in to AgentTaskManager.");
            PreserveConfiguredBackendUrl();
            StatusStrip.ApplySignedOut();
            StatusStrip.SetStreamStatus(Connection.TransportStatus);
            return;
        }

        SignIn.ApplySession(restored);
        await RefreshSessionsAsync();
    }

    private static string NormalizeBaseUrl(string? value)
        => string.IsNullOrWhiteSpace(value)
            ? string.Empty
            : value.Trim().TrimEnd('/').ToUpperInvariant();

    private void StopStreaming()
    {
        if (_streamCancellationTokenSource == null)
        {
            return;
        }

        _streamCancellationTokenSource.Cancel();
        _streamCancellationTokenSource.Dispose();
        _streamCancellationTokenSource = null;
        StatusStrip.SetStreamStatus("Live event stream idle");
    }

}
