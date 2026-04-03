using AgentTaskManager.Desktop.Contracts;
using CommunityToolkit.Mvvm.ComponentModel;

namespace AgentTaskManager.Desktop.ViewModels;

public sealed class OutputGateViewModel : ObservableObject
{
    private string _candidateSummary = "No candidate output.";
    private string _candidateContent = string.Empty;
    private string _approvedSummary = "No approved output.";
    private string _approvedContent = string.Empty;
    private string _statusSummary = "Output gate has not released anything yet.";

    public string CandidateSummary
    {
        get => _candidateSummary;
        set => SetProperty(ref _candidateSummary, value);
    }

    public string CandidateContent
    {
        get => _candidateContent;
        set => SetProperty(ref _candidateContent, value);
    }

    public string ApprovedSummary
    {
        get => _approvedSummary;
        set => SetProperty(ref _approvedSummary, value);
    }

    public string ApprovedContent
    {
        get => _approvedContent;
        set => SetProperty(ref _approvedContent, value);
    }

    public string StatusSummary
    {
        get => _statusSummary;
        set => SetProperty(ref _statusSummary, value);
    }

    public void Apply(OutputSnapshotDto? candidate, OutputSnapshotDto? approved, string statusSummary)
    {
        CandidateSummary = candidate?.Summary ?? "No candidate output.";
        CandidateContent = candidate?.Content ?? string.Empty;
        ApprovedSummary = approved?.Summary ?? "No approved output.";
        ApprovedContent = approved?.Content ?? string.Empty;
        StatusSummary = statusSummary;
    }

    public void Clear()
    {
        Apply(null, null, "Output gate has not released anything yet.");
    }
}
