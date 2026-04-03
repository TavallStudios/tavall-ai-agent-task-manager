using AgentTaskManager.Desktop.Contracts;

namespace AgentTaskManager.Desktop.Services;

public sealed class ToolReceiptService : IToolReceiptService
{
    public string BuildSummary(SessionDetailDto session)
    {
        int missing = session.ToolReceipts.Count(item => !string.Equals(item.Status, "recorded", StringComparison.OrdinalIgnoreCase));
        return missing == 0
            ? "All recorded tool receipts currently shown are backend-issued evidence."
            : $"{missing} tool receipts are pending or non-final.";
    }

    public IReadOnlyList<ToolReceiptDto> OrderReceipts(SessionDetailDto session)
        => session.ToolReceipts
            .OrderByDescending(item => item.RecordedAt)
            .ToList();
}
