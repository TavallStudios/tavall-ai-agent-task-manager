using AgentTaskManager.Desktop.Contracts;
using AgentTaskManager.Desktop.Services;
using AgentTaskManager.Desktop.Utility;
using CommunityToolkit.Mvvm.ComponentModel;
using System.Collections.ObjectModel;

namespace AgentTaskManager.Desktop.ViewModels;

public sealed class SessionDetailViewModel : ObservableObject
{
    private readonly ICodexSupervisorService _codexSupervisorService;
    private readonly IMemoryContextService _memoryContextService;
    private readonly IToolReceiptService _toolReceiptService;
    private readonly IVerifierStatusService _verifierStatusService;
    private readonly IOutputReleaseService _outputReleaseService;
    private string _sessionId = string.Empty;
    private string _sessionTitle = "No session selected";
    private string _workspaceRoot = string.Empty;
    private string _lifecycleState = string.Empty;
    private string _pendingPrompt = string.Empty;
    private string _runtimeBanner = "Runtime disconnected";
    private string _verifierBanner = "Verifier state unavailable";
    private string _receiptBanner = "Tool receipts unavailable";
    private string _memoryBanner = "Memory references unavailable";

    public SessionDetailViewModel(
        ICodexSupervisorService codexSupervisorService,
        IMemoryContextService memoryContextService,
        IToolReceiptService toolReceiptService,
        IVerifierStatusService verifierStatusService,
        IOutputReleaseService outputReleaseService)
    {
        _codexSupervisorService = codexSupervisorService;
        _memoryContextService = memoryContextService;
        _toolReceiptService = toolReceiptService;
        _verifierStatusService = verifierStatusService;
        _outputReleaseService = outputReleaseService;
    }

    public string SessionId
    {
        get => _sessionId;
        set => SetProperty(ref _sessionId, value);
    }

    public string SessionTitle
    {
        get => _sessionTitle;
        set => SetProperty(ref _sessionTitle, value);
    }

    public string WorkspaceRoot
    {
        get => _workspaceRoot;
        set => SetProperty(ref _workspaceRoot, value);
    }

    public string LifecycleState
    {
        get => _lifecycleState;
        set => SetProperty(ref _lifecycleState, value);
    }

    public string PendingPrompt
    {
        get => _pendingPrompt;
        set => SetProperty(ref _pendingPrompt, value);
    }

    public string RuntimeBanner
    {
        get => _runtimeBanner;
        set => SetProperty(ref _runtimeBanner, value);
    }

    public string VerifierBanner
    {
        get => _verifierBanner;
        set => SetProperty(ref _verifierBanner, value);
    }

    public string ReceiptBanner
    {
        get => _receiptBanner;
        set => SetProperty(ref _receiptBanner, value);
    }

    public string MemoryBanner
    {
        get => _memoryBanner;
        set => SetProperty(ref _memoryBanner, value);
    }

    public ObservableCollection<TurnSummaryDto> Turns { get; } = new();

    public ObservableCollection<SessionEventEnvelopeDto> Events { get; } = new();

    public ObservableCollection<ToolReceiptDto> ToolReceipts { get; } = new();

    public ObservableCollection<VerifierResultDto> VerifierResults { get; } = new();

    public ObservableCollection<DevicePresenceDto> Devices { get; } = new();

    public ObservableCollection<MemoryContextReferenceDto> MemoryReferences { get; } = new();

    public OutputGateViewModel OutputGate { get; } = new();

    public PatchReviewViewModel PatchReview { get; } = new();

    public string? LatestEventId => Events.Count == 0 ? null : Events[Events.Count - 1].EventId;

    public void ApplySession(SessionDetailDto detail)
    {
        SessionId = detail.Summary.SessionId;
        SessionTitle = detail.Summary.Title;
        WorkspaceRoot = detail.WorkspaceBinding.WorkspaceRoot;
        LifecycleState = detail.Summary.LifecycleState;
        RuntimeBanner = _codexSupervisorService.BuildRuntimeBanner(detail);
        VerifierBanner = _verifierStatusService.BuildSummary(detail);
        ReceiptBanner = _toolReceiptService.BuildSummary(detail);
        MemoryBanner = _memoryContextService.BuildSummary(detail);
        Turns.ReplaceWith(detail.Turns.OrderByDescending(item => item.LastUpdatedAt));
        Events.ReplaceWith(detail.RecentEvents.OrderBy(item => item.OccurredAt));
        ToolReceipts.ReplaceWith(_toolReceiptService.OrderReceipts(detail));
        VerifierResults.ReplaceWith(_verifierStatusService.OrderResults(detail));
        Devices.ReplaceWith(detail.Devices.OrderByDescending(item => item.LastSeenAt));
        MemoryReferences.ReplaceWith(_memoryContextService.OrderEntries(detail));
        PatchReview.Load(detail.Patches, detail.FileFocusRequests);
        OutputGate.Apply(
            _outputReleaseService.GetLatestCandidate(detail),
            _outputReleaseService.GetLatestApproved(detail),
            _outputReleaseService.BuildSummary(detail));
    }

    public void AppendEvent(SessionEventEnvelopeDto item)
    {
        Events.Add(item);
    }

    public void Clear()
    {
        SessionId = string.Empty;
        SessionTitle = "No session selected";
        WorkspaceRoot = string.Empty;
        LifecycleState = string.Empty;
        PendingPrompt = string.Empty;
        RuntimeBanner = "Runtime disconnected";
        VerifierBanner = "Verifier state unavailable";
        ReceiptBanner = "Tool receipts unavailable";
        MemoryBanner = "Memory references unavailable";
        Turns.Clear();
        Events.Clear();
        ToolReceipts.Clear();
        VerifierResults.Clear();
        Devices.Clear();
        MemoryReferences.Clear();
        PatchReview.Clear();
        OutputGate.Clear();
    }
}
