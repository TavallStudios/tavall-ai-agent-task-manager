namespace AgentTaskManager.Desktop.Contracts;

public enum RepoNextAction
{
    OpenSession,
    OpenFailureDetails,
    ReconnectRuntime,
    WaitForVerifier,
    SubmitNextTurn,
    ReviewApprovedOutput
}

public sealed record RepoTabSummary(
    string RepoKey,
    string RepoLabel,
    string PrimarySessionId,
    string SessionTitle,
    string LifecycleState,
    string RuntimeConnectionState,
    string OutputReleaseState,
    string? ActiveTurnId,
    DateTimeOffset LastEventAt,
    RepoNextAction NextAction)
{
    public string HeaderTitle => RepoLabel;

    public string HeaderSubtitle
        => $"{LifecycleState}  {LastEventAt.ToLocalTime():MMM d HH:mm}";

    public string StatusLine
        => $"{SessionTitle}  [{LifecycleState}]  Runtime {RuntimeConnectionState}";

    public string NextActionLabel => RepoNextActionCatalog.ToLabel(NextAction);
}

public static class RepoNextActionCatalog
{
    public static string ToLabel(RepoNextAction action)
        => action switch
        {
            RepoNextAction.OpenFailureDetails => "Open Failure Details",
            RepoNextAction.ReconnectRuntime => "Reconnect Runtime",
            RepoNextAction.WaitForVerifier => "Wait for Verifier",
            RepoNextAction.SubmitNextTurn => "Submit Next Turn",
            RepoNextAction.ReviewApprovedOutput => "Review Approved Output",
            _ => "Open Session"
        };

    public static string ToDescription(RepoNextAction action)
        => action switch
        {
            RepoNextAction.OpenFailureDetails => "The selected repo session failed. Jump to the latest diagnostics and artifacts.",
            RepoNextAction.ReconnectRuntime => "The session is paused with runtime disconnected. Reconnect local runtime ownership.",
            RepoNextAction.WaitForVerifier => "A turn is active and still waiting for verifier or output gate completion.",
            RepoNextAction.SubmitNextTurn => "Runtime is connected and no active turn is running. Submit the next instruction.",
            RepoNextAction.ReviewApprovedOutput => "Approved output is available. Review output and patch details.",
            _ => "Open the selected repo session context."
        };
}
