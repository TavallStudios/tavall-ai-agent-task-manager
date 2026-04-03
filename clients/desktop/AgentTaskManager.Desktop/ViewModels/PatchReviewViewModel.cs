using AgentTaskManager.Desktop.Contracts;
using AgentTaskManager.Desktop.Utility;
using CommunityToolkit.Mvvm.ComponentModel;
using System.Collections.ObjectModel;

namespace AgentTaskManager.Desktop.ViewModels;

public sealed class PatchReviewViewModel : ObservableObject
{
    private PatchArtifactDto? _selectedPatch;
    private FileFocusRequestDto? _selectedFileFocusRequest;
    private string _patchSummary = "No patch artifacts.";

    public PatchArtifactDto? SelectedPatch
    {
        get => _selectedPatch;
        set
        {
            if (SetProperty(ref _selectedPatch, value))
            {
                OnPropertyChanged(nameof(SelectedDiffPreview));
            }
        }
    }

    public FileFocusRequestDto? SelectedFileFocusRequest
    {
        get => _selectedFileFocusRequest;
        set => SetProperty(ref _selectedFileFocusRequest, value);
    }

    public string PatchSummary
    {
        get => _patchSummary;
        set => SetProperty(ref _patchSummary, value);
    }

    public ObservableCollection<PatchArtifactDto> Patches { get; } = new();

    public ObservableCollection<FileFocusRequestDto> FileFocusRequests { get; } = new();

    public string SelectedDiffPreview => SelectedPatch?.DiffPreview ?? string.Empty;

    public void Load(IEnumerable<PatchArtifactDto> patches, IEnumerable<FileFocusRequestDto> fileFocusRequests)
    {
        Patches.ReplaceWith(patches.OrderByDescending(item => item.RecordedAt));
        FileFocusRequests.ReplaceWith(fileFocusRequests.OrderByDescending(item => item.CreatedAt));
        SelectedPatch = Patches.FirstOrDefault();
        SelectedFileFocusRequest = FileFocusRequests.FirstOrDefault();
        PatchSummary = Patches.Count == 0
            ? "No patch artifacts."
            : $"Showing {Patches.Count} patch artifact(s).";
        OnPropertyChanged(nameof(SelectedDiffPreview));
    }

    public void Clear()
    {
        Patches.Clear();
        FileFocusRequests.Clear();
        SelectedPatch = null;
        SelectedFileFocusRequest = null;
        PatchSummary = "No patch artifacts.";
        OnPropertyChanged(nameof(SelectedDiffPreview));
    }

}
