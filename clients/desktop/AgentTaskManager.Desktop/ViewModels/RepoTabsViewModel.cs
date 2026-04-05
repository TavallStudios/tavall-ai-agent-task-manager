using AgentTaskManager.Desktop.Contracts;
using AgentTaskManager.Desktop.Services;
using AgentTaskManager.Desktop.Utility;
using CommunityToolkit.Mvvm.ComponentModel;
using System.Collections.ObjectModel;

namespace AgentTaskManager.Desktop.ViewModels;

public sealed class RepoTabsViewModel : ObservableObject
{
    private readonly IRepoNextActionService _nextActionService;
    private readonly Dictionary<string, SessionSummaryDto> _sessionsById = new(StringComparer.Ordinal);
    private RepoTabSummary? _selectedTab;
    private string _statusMessage = "No repositories loaded.";

    public RepoTabsViewModel(IRepoNextActionService nextActionService)
    {
        _nextActionService = nextActionService;
    }

    public ObservableCollection<RepoTabSummary> Tabs { get; } = new();

    public RepoTabSummary? SelectedTab
    {
        get => _selectedTab;
        set
        {
            if (!SetProperty(ref _selectedTab, value))
            {
                return;
            }

            OnPropertyChanged(nameof(SelectedNextActionLabel));
            OnPropertyChanged(nameof(SelectedNextActionDescription));
            OnPropertyChanged(nameof(SelectedStatusLine));
        }
    }

    public string StatusMessage
    {
        get => _statusMessage;
        set => SetProperty(ref _statusMessage, value);
    }

    public string SelectedNextActionLabel
        => SelectedTab?.NextActionLabel ?? RepoNextActionCatalog.ToLabel(RepoNextAction.OpenSession);

    public string SelectedNextActionDescription
        => RepoNextActionCatalog.ToDescription(SelectedTab?.NextAction ?? RepoNextAction.OpenSession);

    public string SelectedStatusLine
        => SelectedTab?.StatusLine ?? "Select a repository tab to focus its session.";

    public void ReplaceSessions(IEnumerable<SessionSummaryDto> sessions)
    {
        _sessionsById.Clear();
        foreach (SessionSummaryDto session in sessions)
        {
            _sessionsById[session.SessionId] = session;
        }

        RebuildTabs();
    }

    public void UpsertSession(SessionSummaryDto session)
    {
        _sessionsById[session.SessionId] = session;
        RebuildTabs();
    }

    public void Clear()
    {
        _sessionsById.Clear();
        Tabs.Clear();
        SelectedTab = null;
        StatusMessage = "No repositories loaded.";
    }

    public void SelectTab(string repoKey)
    {
        if (string.IsNullOrWhiteSpace(repoKey))
        {
            return;
        }

        SelectedTab = Tabs.FirstOrDefault(item =>
            string.Equals(item.RepoKey, repoKey, StringComparison.OrdinalIgnoreCase));
    }

    public void SelectTabForSession(string sessionId)
    {
        if (string.IsNullOrWhiteSpace(sessionId))
        {
            return;
        }

        RepoTabSummary? tab = Tabs.FirstOrDefault(item =>
            string.Equals(item.PrimarySessionId, sessionId, StringComparison.Ordinal));
        if (tab is not null)
        {
            SelectedTab = tab;
            return;
        }

        if (!_sessionsById.TryGetValue(sessionId, out SessionSummaryDto? summary))
        {
            return;
        }

        string repoKey = NormalizeRepoKey(summary.RepoPath, summary.WorkspaceRoot);
        SelectTab(repoKey);
    }

    public SessionSummaryDto? GetPrimarySessionForSelectedTab()
    {
        if (SelectedTab == null)
        {
            return null;
        }

        _sessionsById.TryGetValue(SelectedTab.PrimarySessionId, out SessionSummaryDto? summary);
        return summary;
    }

    private void RebuildTabs()
    {
        string? selectedRepoKey = SelectedTab?.RepoKey;
        List<RepoTabSummary> rebuilt = _sessionsById.Values
            .GroupBy(item => NormalizeRepoKey(item.RepoPath, item.WorkspaceRoot), StringComparer.OrdinalIgnoreCase)
            .Select(group => BuildTab(group.Key, group.ToList()))
            .OrderByDescending(item => item.LastEventAt)
            .ThenBy(item => item.RepoLabel, StringComparer.OrdinalIgnoreCase)
            .ToList();

        Tabs.ReplaceWith(rebuilt);
        SelectedTab = ResolveSelection(selectedRepoKey, rebuilt);
        StatusMessage = rebuilt.Count == 0
            ? "No repositories loaded."
            : $"Tracking {rebuilt.Count} repository tab(s).";
    }

    private RepoTabSummary BuildTab(string repoKey, IReadOnlyList<SessionSummaryDto> sessions)
    {
        SessionSummaryDto primary = SelectPrimarySession(sessions);
        return new RepoTabSummary(
            repoKey,
            BuildRepoLabel(repoKey),
            primary.SessionId,
            primary.Title,
            primary.LifecycleState,
            primary.RuntimeConnectionState,
            primary.OutputReleaseState,
            primary.ActiveTurnId,
            primary.LastEventAt,
            _nextActionService.Resolve(primary));
    }

    private static SessionSummaryDto SelectPrimarySession(IReadOnlyList<SessionSummaryDto> sessions)
    {
        SessionSummaryDto? active = sessions
            .Where(IsPreferredLifecycle)
            .OrderByDescending(item => item.LastEventAt)
            .FirstOrDefault();
        return active ?? sessions.OrderByDescending(item => item.LastEventAt).First();
    }

    private static bool IsPreferredLifecycle(SessionSummaryDto summary)
        => string.Equals(summary.LifecycleState, "ACTIVE", StringComparison.OrdinalIgnoreCase)
            || string.Equals(summary.LifecycleState, "ATTACHED", StringComparison.OrdinalIgnoreCase)
            || string.Equals(summary.LifecycleState, "PAUSED", StringComparison.OrdinalIgnoreCase);

    private static RepoTabSummary? ResolveSelection(string? selectedRepoKey, IReadOnlyList<RepoTabSummary> rebuilt)
    {
        if (!string.IsNullOrWhiteSpace(selectedRepoKey))
        {
            RepoTabSummary? matched = rebuilt.FirstOrDefault(item =>
                string.Equals(item.RepoKey, selectedRepoKey, StringComparison.OrdinalIgnoreCase));
            if (matched is not null)
            {
                return matched;
            }
        }

        return rebuilt.FirstOrDefault();
    }

    private static string NormalizeRepoKey(string repoPath, string workspaceRoot)
    {
        string raw = string.IsNullOrWhiteSpace(repoPath) ? workspaceRoot : repoPath;
        return raw.Trim().TrimEnd('\\', '/');
    }

    private static string BuildRepoLabel(string repoKey)
    {
        string normalized = repoKey.Replace('\\', '/').TrimEnd('/');
        int index = normalized.LastIndexOf('/');
        string leaf = index >= 0 ? normalized[(index + 1)..] : normalized;
        return string.IsNullOrWhiteSpace(leaf) ? repoKey : leaf;
    }
}
