using AgentTaskManager.Desktop.Contracts;
using AgentTaskManager.Desktop.Services;
using AgentTaskManager.Desktop.Utility;
using CommunityToolkit.Mvvm.ComponentModel;
using System.Collections.ObjectModel;

namespace AgentTaskManager.Desktop.ViewModels;

public sealed class RemoteRunnerViewModel : ObservableObject
{
    private readonly IRemoteRunnerProfileService _profileService;
    private RemoteRunnerProfileDto? _selectedProfile;
    private string _profileId = string.Empty;
    private string _displayName = "Remote Runner";
    private string _baseUrl = "http://127.0.0.1:54123";
    private string _transportMode = "DIRECT_HTTP";
    private string _sshHost = string.Empty;
    private int _sshPort = 22;
    private string _sshUser = "ubuntu";
    private string _runnerAuthTokenReference = string.Empty;
    private string _runnerAuthToken = string.Empty;
    private string _defaultScenarioId = "hytale/launch-and-join-smoke";
    private string _terminalCommand = string.Empty;
    private string _statusMessage = "Remote runner profiles idle.";
    private string _testSummary = "No runner test executed.";
    private bool _isBusy;

    public RemoteRunnerViewModel(IRemoteRunnerProfileService profileService)
    {
        _profileService = profileService;
    }

    public ObservableCollection<RemoteRunnerProfileDto> Profiles { get; } = new();

    public RemoteRunnerProfileDto? SelectedProfile
    {
        get => _selectedProfile;
        set
        {
            if (!SetProperty(ref _selectedProfile, value) || value is null)
            {
                return;
            }

            LoadEditorFromProfile(value);
        }
    }

    public string ProfileId
    {
        get => _profileId;
        set => SetProperty(ref _profileId, value);
    }

    public string DisplayName
    {
        get => _displayName;
        set => SetProperty(ref _displayName, value);
    }

    public string BaseUrl
    {
        get => _baseUrl;
        set => SetProperty(ref _baseUrl, value);
    }

    public string TransportMode
    {
        get => _transportMode;
        set => SetProperty(ref _transportMode, value);
    }

    public string SshHost
    {
        get => _sshHost;
        set => SetProperty(ref _sshHost, value);
    }

    public int SshPort
    {
        get => _sshPort;
        set => SetProperty(ref _sshPort, value);
    }

    public string SshUser
    {
        get => _sshUser;
        set => SetProperty(ref _sshUser, value);
    }

    public string RunnerAuthTokenReference
    {
        get => _runnerAuthTokenReference;
        set => SetProperty(ref _runnerAuthTokenReference, value);
    }

    public string RunnerAuthToken
    {
        get => _runnerAuthToken;
        set => SetProperty(ref _runnerAuthToken, value);
    }

    public string DefaultScenarioId
    {
        get => _defaultScenarioId;
        set => SetProperty(ref _defaultScenarioId, value);
    }

    public string TerminalCommand
    {
        get => _terminalCommand;
        set => SetProperty(ref _terminalCommand, value);
    }

    public string StatusMessage
    {
        get => _statusMessage;
        set => SetProperty(ref _statusMessage, value);
    }

    public string TestSummary
    {
        get => _testSummary;
        set => SetProperty(ref _testSummary, value);
    }

    public bool IsBusy
    {
        get => _isBusy;
        set => SetProperty(ref _isBusy, value);
    }

    public async Task InitializeAsync(CancellationToken cancellationToken)
    {
        await RefreshAsync(cancellationToken);
    }

    public async Task RefreshAsync(CancellationToken cancellationToken)
    {
        IsBusy = true;
        try
        {
            IReadOnlyList<RemoteRunnerProfileDto> profiles = await _profileService.ListProfilesAsync(cancellationToken);
            Profiles.ReplaceWith(profiles.OrderBy(item => item.DisplayName));
            SelectedProfile = Profiles.FirstOrDefault(item => item.Selected) ?? Profiles.FirstOrDefault();
            StatusMessage = $"Loaded {Profiles.Count} remote runner profile(s).";
        }
        catch (Exception exception)
        {
            StatusMessage = exception.Message;
        }
        finally
        {
            IsBusy = false;
        }
    }

    public void NewProfile()
    {
        ProfileId = Guid.NewGuid().ToString("N");
        DisplayName = "Remote Runner";
        BaseUrl = "http://127.0.0.1:54123";
        TransportMode = "DIRECT_HTTP";
        SshHost = string.Empty;
        SshPort = 22;
        SshUser = "ubuntu";
        RunnerAuthTokenReference = $"runner-token-{ProfileId}";
        RunnerAuthToken = string.Empty;
        DefaultScenarioId = "hytale/launch-and-join-smoke";
        TerminalCommand = string.Empty;
        StatusMessage = "Created a new unsaved runner profile draft.";
    }

    public async Task SaveAsync(CancellationToken cancellationToken)
    {
        IsBusy = true;
        try
        {
            RemoteRunnerProfileDto profile = BuildProfile(selected: SelectedProfile?.Selected ?? false);
            RemoteRunnerProfileDto saved = await _profileService.SaveProfileAsync(profile, RunnerAuthToken, cancellationToken);
            RunnerAuthToken = string.Empty;
            await RefreshAsync(cancellationToken);
            SelectedProfile = Profiles.FirstOrDefault(item => item.ProfileId == saved.ProfileId);
            StatusMessage = $"Saved runner profile '{saved.DisplayName}'.";
        }
        catch (Exception exception)
        {
            StatusMessage = exception.Message;
        }
        finally
        {
            IsBusy = false;
        }
    }

    public async Task DeleteSelectedAsync(CancellationToken cancellationToken)
    {
        if (SelectedProfile is null)
        {
            return;
        }

        IsBusy = true;
        try
        {
            await _profileService.DeleteProfileAsync(SelectedProfile.ProfileId, cancellationToken);
            await RefreshAsync(cancellationToken);
            StatusMessage = "Deleted remote runner profile.";
        }
        catch (Exception exception)
        {
            StatusMessage = exception.Message;
        }
        finally
        {
            IsBusy = false;
        }
    }

    public async Task SelectDefaultAsync(CancellationToken cancellationToken)
    {
        if (SelectedProfile is null)
        {
            return;
        }

        string profileId = SelectedProfile.ProfileId;
        string displayName = SelectedProfile.DisplayName;
        IsBusy = true;
        try
        {
            await _profileService.SelectProfileAsync(profileId, cancellationToken);
            await RefreshAsync(cancellationToken);
            StatusMessage = $"Selected '{displayName}' as default remote profile.";
        }
        catch (Exception exception)
        {
            StatusMessage = exception.Message;
        }
        finally
        {
            IsBusy = false;
        }
    }

    public async Task TestSelectedAsync(CancellationToken cancellationToken)
    {
        RemoteRunnerProfileDto profile = BuildProfile(selected: SelectedProfile?.Selected ?? false);
        IsBusy = true;
        try
        {
            RemoteRunnerConnectionTestDto result = await _profileService.TestProfileAsync(profile, cancellationToken);
            TestSummary = $"{result.Message} Health={result.HealthStatus}; Capabilities={result.CapabilitiesSummary}; Command={result.EffectiveCommandPath}";
            StatusMessage = result.Success ? "Runner test passed." : "Runner test failed.";
        }
        catch (Exception exception)
        {
            TestSummary = exception.Message;
            StatusMessage = "Runner test failed.";
        }
        finally
        {
            IsBusy = false;
        }
    }

    private void LoadEditorFromProfile(RemoteRunnerProfileDto profile)
    {
        ProfileId = profile.ProfileId;
        DisplayName = profile.DisplayName;
        BaseUrl = profile.BaseUrl;
        TransportMode = profile.TransportMode;
        SshHost = profile.SshHost;
        SshPort = profile.SshPort;
        SshUser = profile.SshUser;
        RunnerAuthTokenReference = profile.RunnerAuthTokenReference;
        RunnerAuthToken = string.Empty;
        DefaultScenarioId = profile.DefaultScenarioId;
        TerminalCommand = profile.TerminalCommand;
    }

    private RemoteRunnerProfileDto BuildProfile(bool selected)
        => new(
            string.IsNullOrWhiteSpace(ProfileId) ? Guid.NewGuid().ToString("N") : ProfileId,
            DisplayName,
            BaseUrl,
            TransportMode,
            SshHost,
            SshPort,
            SshUser,
            RunnerAuthTokenReference,
            DefaultScenarioId,
            TerminalCommand,
            selected,
            DateTimeOffset.UtcNow);
}
