using AgentTaskManager.Desktop.ViewModels;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Windows.Graphics;
using AgentTaskManager.Desktop.Services;

namespace AgentTaskManager.Desktop;

public sealed class MainWindow : Window
{
    private readonly FrameworkElement _rootContent;
    private readonly PasswordBox _backendPasswordBox;

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
        controls.SessionListView.SelectionChanged += OnSessionSelectionChanged;
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
        await RunUiActionAsync(() => ViewModel.LoadSelectedSessionAsync());
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
