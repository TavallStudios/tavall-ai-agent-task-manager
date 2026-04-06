using AgentTaskManager.Desktop.Contracts;
using AgentTaskManager.Desktop.Utility;
using CommunityToolkit.Mvvm.ComponentModel;
using System.Collections.ObjectModel;

namespace AgentTaskManager.Desktop.ViewModels;

public sealed class WorkspacePickerViewModel : ObservableObject
{
    private WorkspaceDescriptorDto? _selectedWorkspace;
    private string _sessionTitle = "Desktop Session";
    private string _projectKey = "tavall-ai";
    private string _workspaceRoot = Environment.CurrentDirectory;
    private string _repoPath = Environment.CurrentDirectory;
    private string _profileKey = "workspace-default";
    private string _workspaceScope = "REPOSITORY";
    private string _initialPrompt = "Continue working inside the selected repository.";
    private bool _utilitySession;
    private bool _createRuntime = true;

    public WorkspaceDescriptorDto? SelectedWorkspace
    {
        get => _selectedWorkspace;
        set
        {
            if (!SetProperty(ref _selectedWorkspace, value) || value == null)
            {
                return;
            }

            WorkspaceRoot = value.WorkspaceRoot;
            RepoPath = string.IsNullOrWhiteSpace(value.RepoPath) ? value.WorkspaceRoot : value.RepoPath;
            ProfileKey = value.DefaultProfileKey;
        }
    }

    public string SessionTitle
    {
        get => _sessionTitle;
        set => SetProperty(ref _sessionTitle, value);
    }

    public string ProjectKey
    {
        get => _projectKey;
        set => SetProperty(ref _projectKey, value);
    }

    public string WorkspaceRoot
    {
        get => _workspaceRoot;
        set => SetProperty(ref _workspaceRoot, value);
    }

    public string RepoPath
    {
        get => _repoPath;
        set => SetProperty(ref _repoPath, value);
    }

    public string ProfileKey
    {
        get => _profileKey;
        set => SetProperty(ref _profileKey, value);
    }

    public string WorkspaceScope
    {
        get => _workspaceScope;
        set => SetProperty(ref _workspaceScope, value);
    }

    public string InitialPrompt
    {
        get => _initialPrompt;
        set => SetProperty(ref _initialPrompt, value);
    }

    public bool UtilitySession
    {
        get => _utilitySession;
        set => SetProperty(ref _utilitySession, value);
    }

    public bool CreateRuntime
    {
        get => _createRuntime;
        set => SetProperty(ref _createRuntime, value);
    }

    public ObservableCollection<WorkspaceDescriptorDto> Workspaces { get; } = new();

    public void LoadWorkspaces(IEnumerable<WorkspaceDescriptorDto> workspaces)
    {
        Workspaces.ReplaceWith(workspaces);
        if (SelectedWorkspace != null
            && !Workspaces.Any(item =>
                string.Equals(item.WorkspaceRoot, SelectedWorkspace.WorkspaceRoot, StringComparison.OrdinalIgnoreCase)))
        {
            SelectedWorkspace = null;
        }

        if (SelectedWorkspace == null && Workspaces.Count > 0)
        {
            SelectedWorkspace = Workspaces[0];
        }
    }

    public void ApplyConnectionDefaults(
        string workspaceRoot,
        string repoPath,
        string profileKey,
        bool createRuntimeByDefault)
    {
        if (!string.IsNullOrWhiteSpace(workspaceRoot))
        {
            WorkspaceRoot = workspaceRoot;
        }

        if (!string.IsNullOrWhiteSpace(repoPath))
        {
            RepoPath = repoPath;
        }

        if (!string.IsNullOrWhiteSpace(profileKey))
        {
            ProfileKey = profileKey;
        }

        CreateRuntime = createRuntimeByDefault;
    }

    public CreateSessionRequestDto BuildCreateSessionRequest()
        => new(
            SessionTitle,
            ProjectKey,
            RepoPath,
            WorkspaceRoot,
            ProfileKey,
            "DESKTOP",
            WorkspaceScope,
            UtilitySession,
            CreateRuntime,
            InitialPrompt);
}

