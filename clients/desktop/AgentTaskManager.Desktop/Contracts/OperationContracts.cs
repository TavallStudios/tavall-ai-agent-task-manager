namespace AgentTaskManager.Desktop.Contracts;

public sealed record OperationDescriptorDto(
    string OperationKey,
    string DisplayName,
    string Summary,
    bool Enabled,
    string Source);

public sealed record OperationGroupDto(
    string GroupKey,
    string DisplayName,
    string Summary,
    IReadOnlyList<OperationDescriptorDto> Operations)
{
    public string DisplaySummary => $"{DisplayName}  ({Operations.Count})";
}

