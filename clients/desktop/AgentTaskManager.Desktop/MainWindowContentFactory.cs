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
    public required TabView RepoTabView { get; init; }
    public required Button RepoNextActionButton { get; init; }
    public required Button RefreshOperationsButton { get; init; }
    public required ListView RemoteRunnerProfileListView { get; init; }
    public required Button NewRemoteRunnerProfileButton { get; init; }
    public required Button SaveRemoteRunnerProfileButton { get; init; }
    public required Button DeleteRemoteRunnerProfileButton { get; init; }
    public required Button SelectRemoteRunnerProfileButton { get; init; }
    public required Button TestRemoteRunnerProfileButton { get; init; }
    public required Button RefreshMcpPolicyButton { get; init; }
    public required Button SaveGlobalMcpPolicyButton { get; init; }
    public required Button SaveRepoMcpPolicyButton { get; init; }
    public required Button AddGlobalServerPolicyButton { get; init; }
    public required Button RemoveGlobalServerPolicyButton { get; init; }
    public required Button AddRepoServerPolicyButton { get; init; }
    public required Button RemoveRepoServerPolicyButton { get; init; }
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
        var repoTabView = DesktopAutomationMetadata.WithAutomationId(new TabView
        {
            CanDragTabs = false,
            IsAddTabButtonVisible = false,
            MinHeight = 240
        }, "Tabs_Repos", "Repository Tabs");
        var repoNextActionButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreatePrimaryButton("Open Session"), "Button_RepoNextAction");
        var refreshOperationsButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Refresh Operations"), "Button_RefreshOperations");
        var remoteRunnerProfileListView = DesktopAutomationMetadata.WithAutomationId(new ListView
        {
            MinHeight = 180,
            Background = DesktopShellTheme.InputBackground,
            BorderBrush = DesktopShellTheme.InputBorder,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(14),
            Foreground = DesktopShellTheme.TextPrimary
        }, "List_RemoteRunnerProfiles", "Remote Runner Profiles");
        var newRemoteRunnerProfileButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("New Runner Profile"), "Button_NewRemoteRunnerProfile");
        var saveRemoteRunnerProfileButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreatePrimaryButton("Save Runner Profile"), "Button_SaveRemoteRunnerProfile");
        var deleteRemoteRunnerProfileButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Delete Runner Profile"), "Button_DeleteRemoteRunnerProfile");
        var selectRemoteRunnerProfileButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreatePrimaryButton("Use as Default"), "Button_SelectRemoteRunnerProfile");
        var testRemoteRunnerProfileButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Test Runner"), "Button_TestRemoteRunnerProfile");
        var refreshMcpPolicyButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Refresh Policy"), "Button_RefreshMcpPolicy");
        var saveGlobalMcpPolicyButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreatePrimaryButton("Save Global Policy"), "Button_SaveGlobalMcpPolicy");
        var saveRepoMcpPolicyButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreatePrimaryButton("Save Repo Policy"), "Button_SaveRepoMcpPolicy");
        var addGlobalServerPolicyButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Add Global Server"), "Button_AddGlobalMcpServer");
        var removeGlobalServerPolicyButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Remove Global Server"), "Button_RemoveGlobalMcpServer");
        var addRepoServerPolicyButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Add Repo Server"), "Button_AddRepoMcpServer");
        var removeRepoServerPolicyButton = DesktopAutomationMetadata.WithAutomationId(MainWindowElementFactory.CreateSecondaryButton("Remove Repo Server"), "Button_RemoveRepoMcpServer");
        repoNextActionButton.SetBinding(Button.ContentProperty, Binding("Repos.SelectedNextActionLabel", BindingMode.OneWay));

        BindItems(sessionListView, "SessionList.Sessions");
        sessionListView.DisplayMemberPath = "DisplaySummary";
        sessionListView.SetBinding(Selector.SelectedItemProperty, Binding("SessionList.SelectedSession", BindingMode.TwoWay));
        BindItems(remoteRunnerProfileListView, "RemoteRunners.Profiles");
        remoteRunnerProfileListView.DisplayMemberPath = "DisplaySummary";
        remoteRunnerProfileListView.SetBinding(Selector.SelectedItemProperty, Binding("RemoteRunners.SelectedProfile", BindingMode.TwoWay));

        FrameworkElement workView = MainWindowSectionFactory.BuildWorkPage(
            backendPasswordBox,
            signInButton,
            signOutButton,
            createSessionButton,
            openWorkspaceButton,
            sessionListView,
            resumeSelectedSessionButton,
            submitTurnButton,
            openSelectedPatchButton,
            openSelectedFileButton);
        FrameworkElement operationsView = MainWindowSectionFactory.BuildOperationsPage(
            repoTabView,
            repoNextActionButton,
            refreshOperationsButton);
        FrameworkElement remoteView = MainWindowSectionFactory.BuildRemotePage(
            saveConnectionButton,
            remoteRunnerProfileListView,
            newRemoteRunnerProfileButton,
            saveRemoteRunnerProfileButton,
            deleteRemoteRunnerProfileButton,
            selectRemoteRunnerProfileButton,
            testRemoteRunnerProfileButton);
        FrameworkElement settingsView = MainWindowSectionFactory.BuildSettingsPage(
            useDetectedCodexSetupButton,
            refreshCodexStatusButton,
            startChatGptCodexLoginButton,
            refreshMcpPolicyButton,
            saveGlobalMcpPolicyButton,
            saveRepoMcpPolicyButton,
            addGlobalServerPolicyButton,
            removeGlobalServerPolicyButton,
            addRepoServerPolicyButton,
            removeRepoServerPolicyButton);

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
            ("Work", workView),
            ("Operations", operationsView),
            ("Remote", remoteView),
            ("Settings", settingsView));
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
        contentHost.Children.Add(workView);
        contentHost.Children.Add(operationsView);
        contentHost.Children.Add(remoteView);
        contentHost.Children.Add(settingsView);
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
            OpenSelectedFileButton = openSelectedFileButton,
            RepoTabView = repoTabView,
            RepoNextActionButton = repoNextActionButton,
            RefreshOperationsButton = refreshOperationsButton,
            RemoteRunnerProfileListView = remoteRunnerProfileListView,
            NewRemoteRunnerProfileButton = newRemoteRunnerProfileButton,
            SaveRemoteRunnerProfileButton = saveRemoteRunnerProfileButton,
            DeleteRemoteRunnerProfileButton = deleteRemoteRunnerProfileButton,
            SelectRemoteRunnerProfileButton = selectRemoteRunnerProfileButton,
            TestRemoteRunnerProfileButton = testRemoteRunnerProfileButton,
            RefreshMcpPolicyButton = refreshMcpPolicyButton,
            SaveGlobalMcpPolicyButton = saveGlobalMcpPolicyButton,
            SaveRepoMcpPolicyButton = saveRepoMcpPolicyButton,
            AddGlobalServerPolicyButton = addGlobalServerPolicyButton,
            RemoveGlobalServerPolicyButton = removeGlobalServerPolicyButton,
            AddRepoServerPolicyButton = addRepoServerPolicyButton,
            RemoveRepoServerPolicyButton = removeRepoServerPolicyButton
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
