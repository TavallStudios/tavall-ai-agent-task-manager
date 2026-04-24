using AgentTaskManager.Desktop.Contracts;

namespace AgentTaskManager.Desktop.Services;

public sealed class RepoNextActionService : IRepoNextActionService
{
    public RepoNextAction Resolve(SessionSummaryDto summary)
    {
        if (IsLifecycle(summary, "FAILED"))
        {
            return RepoNextAction.OpenFailureDetails;
        }

        if (IsLifecycle(summary, "PAUSED") && IsRuntime(summary, "DISCONNECTED"))
        {
            return RepoNextAction.ReconnectRuntime;
        }

        if (IsLifecycle(summary, "ACTIVE")
            && !string.IsNullOrWhiteSpace(summary.ActiveTurnId)
            && !IsOutput(summary, "APPROVED"))
        {
            return RepoNextAction.WaitForVerifier;
        }

        if (IsLifecycle(summary, "ACTIVE")
            && IsRuntime(summary, "CONNECTED")
            && string.IsNullOrWhiteSpace(summary.ActiveTurnId))
        {
            return RepoNextAction.SubmitNextTurn;
        }

        if (IsLifecycle(summary, "COMPLETED") && IsOutput(summary, "APPROVED"))
        {
            return RepoNextAction.ReviewApprovedOutput;
        }

        return RepoNextAction.OpenSession;
    }

    private static bool IsLifecycle(SessionSummaryDto summary, string value)
        => string.Equals(summary.LifecycleState, value, StringComparison.OrdinalIgnoreCase);

    private static bool IsRuntime(SessionSummaryDto summary, string value)
        => string.Equals(summary.RuntimeConnectionState, value, StringComparison.OrdinalIgnoreCase);

    private static bool IsOutput(SessionSummaryDto summary, string value)
        => string.Equals(summary.OutputReleaseState, value, StringComparison.OrdinalIgnoreCase);
}
