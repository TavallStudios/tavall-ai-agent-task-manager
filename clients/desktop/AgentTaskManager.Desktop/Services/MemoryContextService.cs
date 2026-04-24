using AgentTaskManager.Desktop.Contracts;

namespace AgentTaskManager.Desktop.Services;

public sealed class MemoryContextService : IMemoryContextService
{
    public string BuildSummary(SessionDetailDto session)
        => session.MemoryReferences.Count == 0
            ? "No memory references for this session yet."
            : $"Showing {session.MemoryReferences.Count} memory or context references from backend-linked retrieval.";

    public IReadOnlyList<MemoryContextReferenceDto> OrderEntries(SessionDetailDto session)
        => session.MemoryReferences
            .OrderByDescending(item => item.RecordedAt)
            .ToList();
}
