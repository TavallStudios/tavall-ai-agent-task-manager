using AgentTaskManager.Desktop.Contracts;

namespace AgentTaskManager.Desktop.Services;

public sealed class OutputReleaseService : IOutputReleaseService
{
    public string BuildSummary(SessionDetailDto session)
    {
        OutputSnapshotDto? approved = GetLatestApproved(session);
        if (approved != null)
        {
            return $"Approved output released at {approved.RecordedAt:yyyy-MM-dd HH:mm:ss}.";
        }

        OutputSnapshotDto? candidate = GetLatestCandidate(session);
        return candidate == null
            ? "No candidate output has been recorded yet."
            : "Candidate output is present but not approved yet.";
    }

    public OutputSnapshotDto? GetLatestCandidate(SessionDetailDto session)
        => session.Outputs
            .Where(item => !item.Approved)
            .OrderByDescending(item => item.RecordedAt)
            .FirstOrDefault();

    public OutputSnapshotDto? GetLatestApproved(SessionDetailDto session)
        => session.Outputs
            .Where(item => item.Approved)
            .OrderByDescending(item => item.RecordedAt)
            .FirstOrDefault();
}
