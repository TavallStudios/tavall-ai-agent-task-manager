using AgentTaskManager.Desktop.Contracts;
using AgentTaskManager.Desktop.Services;
using Xunit;

namespace AgentTaskManager.Desktop.IntegrationTests;

public sealed class RepoNextActionServiceIntegrationTests
{
    private readonly RepoNextActionService _service = new();

    [Fact]
    public void Resolve_FailedSession_ReturnsOpenFailureDetails()
    {
        SessionSummaryDto summary = BuildSummary(lifecycleState: "FAILED");
        Assert.Equal(RepoNextAction.OpenFailureDetails, _service.Resolve(summary));
    }

    [Fact]
    public void Resolve_PausedAndDisconnected_ReturnsReconnectRuntime()
    {
        SessionSummaryDto summary = BuildSummary(
            lifecycleState: "PAUSED",
            runtimeConnectionState: "DISCONNECTED");
        Assert.Equal(RepoNextAction.ReconnectRuntime, _service.Resolve(summary));
    }

    [Fact]
    public void Resolve_ActiveWithTurnAndUnapprovedOutput_ReturnsWaitForVerifier()
    {
        SessionSummaryDto summary = BuildSummary(
            lifecycleState: "ACTIVE",
            outputReleaseState: "CANDIDATE_ONLY",
            activeTurnId: "turn_1");
        Assert.Equal(RepoNextAction.WaitForVerifier, _service.Resolve(summary));
    }

    [Fact]
    public void Resolve_ActiveConnectedAndNoTurn_ReturnsSubmitNextTurn()
    {
        SessionSummaryDto summary = BuildSummary(
            lifecycleState: "ACTIVE",
            runtimeConnectionState: "CONNECTED",
            activeTurnId: null);
        Assert.Equal(RepoNextAction.SubmitNextTurn, _service.Resolve(summary));
    }

    [Fact]
    public void Resolve_CompletedWithApprovedOutput_ReturnsReviewApprovedOutput()
    {
        SessionSummaryDto summary = BuildSummary(
            lifecycleState: "COMPLETED",
            outputReleaseState: "APPROVED");
        Assert.Equal(RepoNextAction.ReviewApprovedOutput, _service.Resolve(summary));
    }

    [Fact]
    public void Resolve_DefaultCase_ReturnsOpenSession()
    {
        SessionSummaryDto summary = BuildSummary(
            lifecycleState: "ATTACHED",
            runtimeConnectionState: "RECONNECTING",
            outputReleaseState: "NONE");
        Assert.Equal(RepoNextAction.OpenSession, _service.Resolve(summary));
    }

    private static SessionSummaryDto BuildSummary(
        string lifecycleState = "ACTIVE",
        string runtimeConnectionState = "CONNECTED",
        string outputReleaseState = "NONE",
        string? activeTurnId = "turn_1")
        => new(
            SessionId: "session_1",
            Title: "Session 1",
            ProjectKey: "agent-task-manager",
            RepoPath: @"F:\workspace\AgentTaskManager",
            WorkspaceRoot: @"F:\workspace\AgentTaskManager",
            ClientSurface: "DESKTOP",
            LifecycleState: lifecycleState,
            RuntimeConnectionState: runtimeConnectionState,
            OutputReleaseState: outputReleaseState,
            RemotelyResumable: true,
            CreatedAt: DateTimeOffset.UtcNow.AddMinutes(-5),
            LastEventAt: DateTimeOffset.UtcNow,
            ActiveTurnId: activeTurnId,
            RuntimeId: "runtime_1");
}
