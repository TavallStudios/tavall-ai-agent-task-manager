using AgentTaskManager.Desktop.Contracts;
using AgentTaskManager.Desktop.Services;
using AgentTaskManager.Desktop.ViewModels;
using Xunit;

namespace AgentTaskManager.Desktop.IntegrationTests;

public sealed class RepoTabsViewModelIntegrationTests
{
    private readonly RepoTabsViewModel _viewModel = new(new RepoNextActionService());

    [Fact]
    public void ReplaceSessions_GroupsByRepoKey_UsingRepoPathOrWorkspaceFallback()
    {
        DateTimeOffset now = DateTimeOffset.UtcNow;
        _viewModel.ReplaceSessions([
            BuildSummary("a1", @"F:\repo\a", @"F:\repo\a", "ACTIVE", now.AddMinutes(-1)),
            BuildSummary("a2", @"F:\repo\a", @"F:\repo\a", "COMPLETED", now),
            BuildSummary("b1", "", @"F:\workspace\b", "ATTACHED", now.AddMinutes(-2))
        ]);

        Assert.Equal(2, _viewModel.Tabs.Count);
        Assert.Contains(_viewModel.Tabs, item => item.RepoKey == @"F:\repo\a");
        Assert.Contains(_viewModel.Tabs, item => item.RepoKey == @"F:\workspace\b");
    }

    [Fact]
    public void ReplaceSessions_SelectsMostRecentPreferredLifecycle_First()
    {
        DateTimeOffset now = DateTimeOffset.UtcNow;
        _viewModel.ReplaceSessions([
            BuildSummary("session_completed", @"F:\repo\a", @"F:\repo\a", "COMPLETED", now),
            BuildSummary("session_active", @"F:\repo\a", @"F:\repo\a", "ACTIVE", now.AddMinutes(-1))
        ]);

        RepoTabSummary tab = Assert.Single(_viewModel.Tabs);
        Assert.Equal("session_active", tab.PrimarySessionId);
        Assert.Equal("ACTIVE", tab.LifecycleState);
    }

    [Fact]
    public void ReplaceSessions_WithoutPreferredLifecycle_FallsBackToMostRecent()
    {
        DateTimeOffset now = DateTimeOffset.UtcNow;
        _viewModel.ReplaceSessions([
            BuildSummary("session_completed_old", @"F:\repo\a", @"F:\repo\a", "COMPLETED", now.AddMinutes(-2)),
            BuildSummary("session_failed_new", @"F:\repo\a", @"F:\repo\a", "FAILED", now)
        ]);

        RepoTabSummary tab = Assert.Single(_viewModel.Tabs);
        Assert.Equal("session_failed_new", tab.PrimarySessionId);
        Assert.Equal("FAILED", tab.LifecycleState);
    }

    [Fact]
    public void SelectTabForSession_SelectsMatchingRepoTab()
    {
        DateTimeOffset now = DateTimeOffset.UtcNow;
        _viewModel.ReplaceSessions([
            BuildSummary("session_a", @"F:\repo\a", @"F:\repo\a", "ACTIVE", now),
            BuildSummary("session_b", @"F:\repo\b", @"F:\repo\b", "ACTIVE", now.AddMinutes(-1))
        ]);

        _viewModel.SelectTabForSession("session_b");

        Assert.NotNull(_viewModel.SelectedTab);
        Assert.Equal(@"F:\repo\b", _viewModel.SelectedTab!.RepoKey);
        Assert.Equal("session_b", _viewModel.GetPrimarySessionForSelectedTab()!.SessionId);
    }

    [Fact]
    public void ReplaceSessions_UpdatesSelectedNextActionMetadata()
    {
        DateTimeOffset now = DateTimeOffset.UtcNow;
        _viewModel.ReplaceSessions([
            BuildSummary(
                sessionId: "session_failed",
                repoPath: @"F:\repo\a",
                workspaceRoot: @"F:\repo\a",
                lifecycleState: "FAILED",
                lastEventAt: now)
        ]);

        Assert.Equal("Open Failure Details", _viewModel.SelectedNextActionLabel);
        Assert.Contains("failed", _viewModel.SelectedNextActionDescription, StringComparison.OrdinalIgnoreCase);
    }

    private static SessionSummaryDto BuildSummary(
        string sessionId,
        string repoPath,
        string workspaceRoot,
        string lifecycleState,
        DateTimeOffset lastEventAt)
        => new(
            SessionId: sessionId,
            Title: sessionId,
            ProjectKey: "agent-task-manager",
            RepoPath: repoPath,
            WorkspaceRoot: workspaceRoot,
            ClientSurface: "DESKTOP",
            LifecycleState: lifecycleState,
            RuntimeConnectionState: "CONNECTED",
            OutputReleaseState: "NONE",
            RemotelyResumable: true,
            CreatedAt: lastEventAt.AddMinutes(-10),
            LastEventAt: lastEventAt,
            ActiveTurnId: lifecycleState == "ACTIVE" ? "turn_1" : null,
            RuntimeId: "runtime_1");
}
