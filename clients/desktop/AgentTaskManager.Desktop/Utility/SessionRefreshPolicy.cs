using AgentTaskManager.Desktop.Contracts;

namespace AgentTaskManager.Desktop.Utility;

internal static class SessionRefreshPolicy
{
    public static bool ShouldRefresh(string eventType)
        => eventType is SessionEventTypes.ToolReceiptPublished
            or SessionEventTypes.VerifierFailed
            or SessionEventTypes.VerifierPassed
            or SessionEventTypes.CandidateOutputProduced
            or SessionEventTypes.ApprovedOutputReleased
            or SessionEventTypes.OutputBlocked
            or SessionEventTypes.PatchPublished
            or SessionEventTypes.RuntimeDisconnected
            or SessionEventTypes.RuntimeReconnected
            or SessionEventTypes.SessionResumed;
}
