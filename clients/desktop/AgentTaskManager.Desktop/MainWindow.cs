using AgentTaskManager.Desktop.ViewModels;
using System.Collections.Specialized;
using System.ComponentModel;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Windows.Graphics;
using AgentTaskManager.Desktop.Services;

namespace AgentTaskManager.Desktop;

public sealed class MainWindow : Window
{
    private readonly FrameworkElement _rootContent;
    private readonly PasswordBox _backendPasswordBox;
    private readonly RepoTabViewRenderer _repoTabViewRenderer;
    private bool _repoTabsRebuildPending;

    public MainShellViewModel ViewModel { get; }

    public MainWindow(MainShellViewModel viewModel)
    {
        ViewModel = viewModel;
        Title = "AgentTaskManager Desktop";
        MainWindowControls controls = MainWindowContentFactory.Create();
        _rootContent = controls.Root;
        _rootContent.DataContext = ViewModel;
        _rootContent.Loaded += OnRootLoaded;
        _backendPasswordBox = controls.BackendPasswordBox;
        _repoTabViewRenderer = new RepoTabViewRenderer(controls.RepoTabView);
        controls.SignInButton.Click += OnSignIn;
        controls.SignOutButton.Click += OnSignOut;
        controls.SaveConnectionButton.Click += OnSaveConnectionProfile;
        controls.DetectRemoteDefaultsButton.Click += OnDetectRemoteDefaults;
        controls.UseLocalModeButton.Click += OnUseLocalMode;
        controls.UseRemoteModeButton.Click += OnUseRemoteMode;
        controls.ConnectTransportButton.Click += OnConnectTransport;
        controls.DisconnectTransportButton.Click += OnDisconnectTransport;
        controls.UseDetectedCodexSetupButton.Click += OnUseDetectedCodexSetup;
        controls.RefreshCodexStatusButton.Click += OnRefreshCodexStatus;
        controls.StartChatGptCodexLoginButton.Click += OnStartChatGptCodexLogin;
        controls.CreateSessionButton.Click += OnCreateSession;
        controls.OpenWorkspaceButton.Click += OnOpenSelectedWorkspace;
        controls.ResumeSelectedSessionButton.Click += OnResumeSelectedSession;
        controls.SubmitTurnButton.Click += OnSubmitTurn;
        controls.OpenSelectedPatchButton.Click += OnOpenSelectedPatch;
        controls.OpenSelectedFileButton.Click += OnOpenSelectedFile;
        controls.RepoNextActionButton.Click += OnRunRepoNextAction;
        controls.RefreshOperationsButton.Click += OnRefreshOperations;
        controls.NewRemoteRunnerProfileButton.Click += OnNewRemoteRunnerProfile;
        controls.SaveRemoteRunnerProfileButton.Click += OnSaveRemoteRunnerProfile;
        controls.DeleteRemoteRunnerProfileButton.Click += OnDeleteRemoteRunnerProfile;
        controls.SelectRemoteRunnerProfileButton.Click += OnSelectRemoteRunnerProfile;
        controls.TestRemoteRunnerProfileButton.Click += OnTestRemoteRunnerProfile;
        controls.RefreshMcpPolicyButton.Click += OnRefreshMcpPolicy;
        controls.SaveGlobalMcpPolicyButton.Click += OnSaveGlobalMcpPolicy;
        controls.SaveRepoMcpPolicyButton.Click += OnSaveRepoMcpPolicy;
        controls.AddGlobalServerPolicyButton.Click += OnAddGlobalServerPolicy;
        controls.RemoveGlobalServerPolicyButton.Click += OnRemoveGlobalServerPolicy;
        controls.AddRepoServerPolicyButton.Click += OnAddRepoServerPolicy;
        controls.RemoveRepoServerPolicyButton.Click += OnRemoveRepoServerPolicy;
        controls.RepoTabView.SelectionChanged += OnRepoTabSelectionChanged;
        controls.SessionListView.SelectionChanged += OnSessionSelectionChanged;
        ViewModel.Repos.Tabs.CollectionChanged += OnRepoTabsCollectionChanged;
        ViewModel.Repos.PropertyChanged += OnRepoTabsPropertyChanged;
        RebuildRepoTabs();
        Closed += OnWindowClosed;
        Content = _rootContent;

        try
        {
            AppWindow.Title = Title;
            AppWindow.Resize(new SizeInt32(1440, 960));
        }
        catch
        {
        }
    }

    private async void OnRootLoaded(object sender, RoutedEventArgs e)
    {
        DesktopDiagnosticsLog.WriteInfo("Main window root loaded. Initializing shell view model.");
        await RunUiActionAsync(() => ViewModel.InitializeAsync());
    }

    private async void OnWindowClosed(object sender, WindowEventArgs args)
    {
        DesktopDiagnosticsLog.WriteInfo("Main window closing. Shutting down managed desktop services.");
        try
        {
            ViewModel.Repos.Tabs.CollectionChanged -= OnRepoTabsCollectionChanged;
            ViewModel.Repos.PropertyChanged -= OnRepoTabsPropertyChanged;
            await ViewModel.ShutdownAsync();
        }
        catch (Exception exception)
        {
            DesktopDiagnosticsLog.WriteError("Desktop shutdown failed.", exception);
        }
    }

    private async void OnSignIn(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(async () =>
        {
            await ViewModel.SignInAsync(_backendPasswordBox.Password);
            _backendPasswordBox.Password = string.Empty;
        });
    }

    private async void OnSignOut(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.SignOutAsync());
    }

    private async void OnSaveConnectionProfile(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.SaveConnectionAsync());
    }

    private async void OnDetectRemoteDefaults(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.DetectRemoteDefaultsAsync());
    }

    private async void OnUseLocalMode(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.UseLocalModeAsync());
    }

    private async void OnUseRemoteMode(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.UseRemoteModeAsync());
    }

    private async void OnConnectTransport(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.ConnectTransportAsync());
    }

    private async void OnDisconnectTransport(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.DisconnectTransportAsync());
    }

    private async void OnUseDetectedCodexSetup(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.UseDetectedCodexSetupAsync());
    }

    private async void OnRefreshCodexStatus(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.RefreshCodexStatusAsync());
    }

    private async void OnStartChatGptCodexLogin(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.StartChatGptCodexLoginAsync());
    }

    private async void OnCreateSession(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.CreateSessionAsync());
    }

    private async void OnSessionSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        await RunUiActionAsync(async () =>
        {
            await ViewModel.LoadSelectedSessionAsync();
            ViewModel.SyncRepoSelectionFromSessionList();
        });
    }

    private async void OnResumeSelectedSession(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.ResumeSelectedSessionAsync());
    }

    private async void OnSubmitTurn(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.SubmitTurnAsync());
    }

    private async void OnOpenSelectedWorkspace(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.OpenSelectedWorkspaceAsync());
    }

    private async void OnOpenSelectedPatch(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.OpenSelectedPatchAsync());
    }

    private async void OnOpenSelectedFile(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.OpenSelectedFileAsync());
    }

    private async void OnRepoTabSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_repoTabViewRenderer.IsSyncing)
        {
            return;
        }

        if (!_repoTabViewRenderer.TryGetSelectedRepoKey(out string repoKey))
        {
            return;
        }

        await RunUiActionAsync(async () =>
        {
            ViewModel.SelectRepoTab(repoKey);
            await ViewModel.LoadSelectedRepoTabAsync();
        });
    }

    private async void OnRunRepoNextAction(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.RunSelectedRepoNextActionAsync());
    }

    private async void OnRefreshOperations(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.Operations.RefreshAsync(CancellationToken.None));
    }

    private async void OnNewRemoteRunnerProfile(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() =>
        {
            ViewModel.RemoteRunners.NewProfile();
            return Task.CompletedTask;
        });
    }

    private async void OnSaveRemoteRunnerProfile(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.RemoteRunners.SaveAsync(CancellationToken.None));
    }

    private async void OnDeleteRemoteRunnerProfile(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.RemoteRunners.DeleteSelectedAsync(CancellationToken.None));
    }

    private async void OnSelectRemoteRunnerProfile(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(async () =>
        {
            await ViewModel.RemoteRunners.SelectDefaultAsync(CancellationToken.None);
            if (ViewModel.RemoteRunners.SelectedProfile is { } selected)
            {
                ViewModel.Connection.PreferredProfileKey = selected.ProfileId;
                await ViewModel.SaveConnectionAsync();
            }
        });
    }

    private async void OnTestRemoteRunnerProfile(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.RemoteRunners.TestSelectedAsync(CancellationToken.None));
    }

    private async void OnRefreshMcpPolicy(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.McpPolicy.RefreshAsync(CancellationToken.None));
    }

    private async void OnSaveGlobalMcpPolicy(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.McpPolicy.SaveGlobalAsync(CancellationToken.None));
    }

    private async void OnSaveRepoMcpPolicy(object sender, RoutedEventArgs e)
    {
        await RunUiActionAsync(() => ViewModel.McpPolicy.SaveRepoAsync(CancellationToken.None));
    }

    private void OnAddGlobalServerPolicy(object sender, RoutedEventArgs e)
    {
        ViewModel.McpPolicy.AddGlobalServerPolicy();
    }

    private void OnRemoveGlobalServerPolicy(object sender, RoutedEventArgs e)
    {
        ViewModel.McpPolicy.RemoveSelectedGlobalServerPolicy();
    }

    private void OnAddRepoServerPolicy(object sender, RoutedEventArgs e)
    {
        ViewModel.McpPolicy.AddRepoServerPolicy();
    }

    private void OnRemoveRepoServerPolicy(object sender, RoutedEventArgs e)
    {
        ViewModel.McpPolicy.RemoveSelectedRepoServerPolicy();
    }

    private void OnRepoTabsCollectionChanged(object? sender, NotifyCollectionChangedEventArgs e)
    {
        if (_repoTabsRebuildPending)
        {
            return;
        }

        _repoTabsRebuildPending = true;
        DispatcherQueue.TryEnqueue(() =>
        {
            _repoTabsRebuildPending = false;
            RebuildRepoTabs();
        });
    }

    private void OnRepoTabsPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(RepoTabsViewModel.SelectedTab))
        {
            _repoTabViewRenderer.SyncSelected(ViewModel.Repos.SelectedTab?.RepoKey);
        }
    }

    private void RebuildRepoTabs()
        => _repoTabViewRenderer.Rebuild(ViewModel.Repos.Tabs, ViewModel.Repos.SelectedTab?.RepoKey);

    private async Task RunUiActionAsync(Func<Task> action)
    {
        try
        {
            await action();
        }
        catch (Exception exception)
        {
            DesktopDiagnosticsLog.WriteError("Desktop UI action failed.", exception);
            ViewModel.HandleError(exception);
        }
    }
}
