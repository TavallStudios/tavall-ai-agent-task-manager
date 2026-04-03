using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Data;

namespace AgentTaskManager.Desktop;

internal sealed class MainWindowControls
{
    public required FrameworkElement Root { get; init; }
    public required PasswordBox BackendPasswordBox { get; init; }
    public required ListView SessionListView { get; init; }
    public required Button SignInButton { get; init; }
    public required Button SignOutButton { get; init; }
    public required Button SaveConnectionButton { get; init; }
    public required Button DetectRemoteDefaultsButton { get; init; }
    public required Button UseLocalModeButton { get; init; }
    public required Button UseRemoteModeButton { get; init; }
    public required Button ConnectTransportButton { get; init; }
    public required Button DisconnectTransportButton { get; init; }
    public required Button UseDetectedCodexSetupButton { get; init; }
    public required Button RefreshCodexStatusButton { get; init; }
    public required Button StartChatGptCodexLoginButton { get; init; }
    public required Button CreateSessionButton { get; init; }
    public required Button OpenWorkspaceButton { get; init; }
    public required Button ResumeSelectedSessionButton { get; init; }
    public required Button SubmitTurnButton { get; init; }
    public required Button OpenSelectedPatchButton { get; init; }
    public required Button OpenSelectedFileButton { get; init; }
}

internal static class MainWindowContentFactory
{
    public static MainWindowControls Create()
    {
        var backendPasswordBox = DesktopAutomationMetadata.WithAutomationId(new PasswordBox
        {
            Background = DesktopShellTheme.InputBackground,
            BorderBrush = DesktopShellTheme.InputBorder,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(12),
            Foreground = DesktopShellTheme.TextPrimary,
            FontFamily = DesktopShellTheme.BodyFont
        }, "Password_Backend", "Backend Password");
        var sessionListView = DesktopAutomationMetadata.WithAutomationId(new ListView
        {
            MinHeight = 260,
            Background = DesktopShellTheme.InputBackground,
            BorderBrush = DesktopShellTheme.InputBorder,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(14),
            Foreground = DesktopShellTheme.TextPrimary
        }, "List_Sessions", "Sessions");
        var signInButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreatePrimaryButton("Sign In"), "Button_SignIn");
        var signOutButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Sign Out"), "Button_SignOut");
        var saveConnectionButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreatePrimaryButton("Save Profile"), "Button_SaveProfile");
        var detectRemoteDefaultsButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Detect Remote"), "Button_DetectRemoteDefaults");
        var useLocalModeButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Use Local"), "Button_UseLocalMode");
        var useRemoteModeButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreatePrimaryButton("Use Remote"), "Button_UseRemoteMode");
        var connectTransportButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreatePrimaryButton("Connect Tunnel"), "Button_ConnectTunnel");
        var disconnectTransportButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Disconnect Tunnel"), "Button_DisconnectTunnel");
        var useDetectedCodexSetupButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreatePrimaryButton("Use Installed Codex Setup"), "Button_UseDetectedCodexSetup");
        var refreshCodexStatusButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Refresh Codex Status"), "Button_RefreshCodexStatus");
        var startChatGptCodexLoginButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Sign In with ChatGPT"), "Button_StartChatGptCodexLogin");
        var createSessionButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreatePrimaryButton("Create Session"), "Button_CreateSession");
        var openWorkspaceButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Open Workspace"), "Button_OpenWorkspace");
        var resumeSelectedSessionButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreatePrimaryButton("Resume Selected Session"), "Button_ResumeSelectedSession");
        var submitTurnButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreatePrimaryButton("Submit Turn"), "Button_SubmitTurn");
        var openSelectedPatchButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Open Selected Patch Preview"), "Button_OpenSelectedPatchPreview");
        var openSelectedFileButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Open Requested File"), "Button_OpenRequestedFile");

        BindItems(sessionListView, "SessionList.Sessions");
        sessionListView.DisplayMemberPath = "DisplaySummary";
        sessionListView.SetBinding(Selector.SelectedItemProperty, Binding("SessionList.SelectedSession", BindingMode.TwoWay));

        FrameworkElement accessView = MainWindowSectionFactory.BuildAccessPage(
            backendPasswordBox,
            signInButton,
            signOutButton,
            saveConnectionButton,
            useDetectedCodexSetupButton,
            refreshCodexStatusButton,
            startChatGptCodexLoginButton,
            createSessionButton,
            openWorkspaceButton);
        FrameworkElement sessionsView = MainWindowSectionFactory.BuildSessionsPage(
            sessionListView,
            resumeSelectedSessionButton,
            submitTurnButton);
        FrameworkElement outputView = MainWindowSectionFactory.BuildOutputPage();
        FrameworkElement reviewView = MainWindowSectionFactory.BuildReviewPage(
            openSelectedPatchButton,
            openSelectedFileButton);
        FrameworkElement diagnosticsView = MainWindowSectionFactory.BuildDiagnosticsPage();

        var root = DesktopAutomationMetadata.WithAutomationId(new Grid
        {
            Background = MainWindowElementFactory.ShellBackgroundBrush,
            RequestedTheme = ElementTheme.Dark
        }, "Root_MainShell", "AgentTaskManager Desktop");
        root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(276) });
        root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

        FrameworkElement sidebar = MainWindowShellChromeFactory.BuildSidebar(
            useLocalModeButton,
            useRemoteModeButton,
            detectRemoteDefaultsButton,
            connectTransportButton,
            disconnectTransportButton,
            ("Access", accessView),
            ("Sessions", sessionsView),
            ("Output", outputView),
            ("Review", reviewView),
            ("Diagnostics", diagnosticsView));
        Grid.SetColumn(sidebar, 0);
        root.Children.Add(sidebar);

        var mainGrid = new Grid { Margin = new Thickness(0, 0, 0, 16) };
        mainGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        mainGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        mainGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        Grid.SetColumn(mainGrid, 1);
        root.Children.Add(mainGrid);

        FrameworkElement header = MainWindowShellChromeFactory.BuildHeaderPanel();
        Grid.SetRow(header, 0);
        mainGrid.Children.Add(header);

        FrameworkElement metricStrip = MainWindowShellChromeFactory.BuildMetricStrip();
        Grid.SetRow(metricStrip, 1);
        mainGrid.Children.Add(metricStrip);

        var contentHost = new Grid { Margin = new Thickness(0, 0, 16, 0) };
        contentHost.Children.Add(accessView);
        contentHost.Children.Add(sessionsView);
        contentHost.Children.Add(outputView);
        contentHost.Children.Add(reviewView);
        contentHost.Children.Add(diagnosticsView);
        Grid.SetRow(contentHost, 2);
        mainGrid.Children.Add(contentHost);

        return new MainWindowControls
        {
            Root = root,
            BackendPasswordBox = backendPasswordBox,
            SessionListView = sessionListView,
            SignInButton = signInButton,
            SignOutButton = signOutButton,
            SaveConnectionButton = saveConnectionButton,
            DetectRemoteDefaultsButton = detectRemoteDefaultsButton,
            UseLocalModeButton = useLocalModeButton,
            UseRemoteModeButton = useRemoteModeButton,
            ConnectTransportButton = connectTransportButton,
            DisconnectTransportButton = disconnectTransportButton,
            UseDetectedCodexSetupButton = useDetectedCodexSetupButton,
            RefreshCodexStatusButton = refreshCodexStatusButton,
            StartChatGptCodexLoginButton = startChatGptCodexLoginButton,
            CreateSessionButton = createSessionButton,
            OpenWorkspaceButton = openWorkspaceButton,
            ResumeSelectedSessionButton = resumeSelectedSessionButton,
            SubmitTurnButton = submitTurnButton,
            OpenSelectedPatchButton = openSelectedPatchButton,
            OpenSelectedFileButton = openSelectedFileButton
        };
    }

    private static void BindItems(ItemsControl control, string path)
        => control.SetBinding(ItemsControl.ItemsSourceProperty, Binding(path));

    private static Binding Binding(string path, BindingMode mode = BindingMode.OneWay)
        => new()
        {
            Path = new PropertyPath(path),
            Mode = mode
        };
}
