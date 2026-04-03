using AgentTaskManager.Desktop.Contracts;

namespace AgentTaskManager.Desktop.Services;

public sealed class VerifierStatusService : IVerifierStatusService
{
    public string BuildSummary(SessionDetailDto session)
    {
        VerifierResultDto? blockingFailure = session.VerifierResults
            .Where(item => item.Blocking)
            .OrderByDescending(item => item.RecordedAt)
            .FirstOrDefault(item => string.Equals(item.Status, "failed", StringComparison.OrdinalIgnoreCase));
        if (blockingFailure != null)
        {
            return $"Blocking verifier failure: {blockingFailure.Summary}";
        }

        return session.VerifierResults.Count == 0
            ? "Verifier has not produced evidence for this session yet."
            : "Verifier history is present and no blocking failure is currently recorded.";
    }

    public IReadOnlyList<VerifierResultDto> OrderResults(SessionDetailDto session)
        => session.VerifierResults
            .OrderByDescending(item => item.RecordedAt)
            .ToList();
}
