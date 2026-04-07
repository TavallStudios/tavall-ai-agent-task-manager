using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace AgentTaskManager.Desktop;

internal static class MainWindowSectionFactory
{
    internal static FrameworkElement BuildWorkPage(
        PasswordBox backendPasswordBox,
        Button signInButton,
        Button signOutButton,
        Button createSessionButton,
        Button openWorkspaceButton,
        ListView sessionListView,
        Button resumeSelectedSessionButton,
        Button submitTurnButton,
        Button openSelectedPatchButton,
        Button openSelectedFileButton)
    {
        var grid = MainWindowElementFactory.CreateTwoColumnGrid();
        grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Access",
            "Identity and session launch",
            "Sign in, launch new sessions, and attach to existing work from one work surface.",
            MainWindowElementFactory.ReadOnlyBoundValue("Backend target", "SignIn.BackendUrl"),
            MainWindowElementFactory.BoundTextBox("User name", "SignIn.UserName"),
            MainWindowElementFactory.LabeledField("Password", backendPasswordBox),
            MainWindowElementFactory.HorizontalButtons(signInButton, signOutButton),
            MainWindowElementFactory.BoundTextBlock("SignIn.StatusMessage", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.HorizontalButtons(createSessionButton, openWorkspaceButton)), 0, 0);

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Session",
            "Session control",
            "Resume selected work, review recent turns, and submit the next turn.",
            MainWindowElementFactory.BoundTextBlock("SessionList.StatusMessage", foreground: MainWindowElementFactory.TextSecondaryBrush),
            sessionListView,
            MainWindowElementFactory.HorizontalButtons(resumeSelectedSessionButton, submitTurnButton),
            MainWindowElementFactory.BoundTextBlock("SessionDetail.SessionTitle", fontWeight: MainWindowElementFactory.SemiBoldWeight),
            MainWindowElementFactory.BoundTextBlock("SessionDetail.RuntimeBanner", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundMultilineBodyTextBox("SessionDetail.PendingPrompt", 120)), 0, 1);

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Approval",
            "Output gate",
            "Approval remains fail-closed. Candidate and approved outputs are split by design.",
            MainWindowElementFactory.BoundTextBlock("SessionDetail.OutputGate.StatusSummary", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.Label("Candidate", MainWindowElementFactory.SemiBoldWeight, 15),
            MainWindowElementFactory.BoundReadOnlyBodyTextBox("SessionDetail.OutputGate.CandidateContent", 130),
            MainWindowElementFactory.Label("Approved", MainWindowElementFactory.SemiBoldWeight, 15),
            MainWindowElementFactory.BoundReadOnlyBodyTextBox("SessionDetail.OutputGate.ApprovedContent", 130),
            MainWindowElementFactory.BoundTextBlock("SessionDetail.VerifierBanner", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("SessionDetail.ReceiptBanner", foreground: MainWindowElementFactory.TextSecondaryBrush)), 1, 0);

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Review",
            "Patch and file focus",
            "Review patch artifacts and requested file-focus paths before accepting outcomes.",
            MainWindowElementFactory.BoundTextBlock("SessionDetail.PatchReview.PatchSummary", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.HorizontalButtons(openSelectedPatchButton, openSelectedFileButton),
            MainWindowElementFactory.BoundSelectableListView(
                "SessionDetail.PatchReview.Patches",
                "SessionDetail.PatchReview.SelectedPatch",
                160,
                displayMemberPath: "DisplaySummary"),
            MainWindowElementFactory.BoundSelectableListView(
                "SessionDetail.PatchReview.FileFocusRequests",
                "SessionDetail.PatchReview.SelectedFileFocusRequest",
                160,
                displayMemberPath: "DisplaySummary"),
            MainWindowElementFactory.BoundReadOnlyBodyTextBox("SessionDetail.PatchReview.SelectedDiffPreview", 150)), 1, 1);

        return MainWindowElementFactory.BuildPage(
            "Work",
            "Session Workbench",
            "Create, resume, review, and approve session work in a single desktop workbench.",
            grid);
    }

    internal static FrameworkElement BuildOperationsPage(
        TabView repoTabView,
        Button repoNextActionButton,
        Button refreshOperationsButton)
    {
        var grid = MainWindowElementFactory.CreateTwoColumnGrid();
        grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Operations",
            "Operation catalog",
            "Operation groups are discovered from backend catalog responses with a local fallback catalog.",
            MainWindowElementFactory.HorizontalButtons(refreshOperationsButton),
            MainWindowElementFactory.BoundTextBlock("Operations.StatusMessage", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundSelectableListView(
                "Operations.Groups",
                "Operations.SelectedGroup",
                180,
                displayMemberPath: "DisplaySummary"),
            MainWindowElementFactory.BoundListView(
                "Operations.SelectedGroup.Operations",
                maxHeight: 220,
                displayMemberPath: "DisplayName")), 0, 0);

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Repos",
            "Repo action board",
            "Repo tabs surface the next action labels derived from session state.",
            repoTabView,
            repoNextActionButton), 0, 1);

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Memory",
            "Memory and device context",
            "Track the memory references and attached devices that influence active operations.",
            MainWindowElementFactory.BoundTextBlock("SessionDetail.MemoryBanner", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundListView("SessionDetail.MemoryReferences", maxHeight: 220, displayMemberPath: "DisplaySummary"),
            MainWindowElementFactory.BoundListView("SessionDetail.Devices", maxHeight: 200, displayMemberPath: "DisplaySummary")), 1, 0);

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Diagnostics",
            "Execution banners",
            "Compatibility-backed state remains visible for diagnostics even when hidden from primary workflows.",
            MainWindowElementFactory.BoundTextBlock("Connection.TransportStatus", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("SessionDetail.RuntimeBanner", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("SessionDetail.VerifierBanner", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("StatusStrip.StreamStatus", foreground: MainWindowElementFactory.TextSecondaryBrush)), 1, 1);

        return MainWindowElementFactory.BuildPage(
            "Operations",
            "Operation Surface",
            "Delegation, memory, and computer-use operation groups feed this unified operations surface.",
            grid);
    }

    internal static FrameworkElement BuildRemotePage(
        Button saveConnectionButton,
        ListView remoteRunnerProfileListView,
        Button newRemoteRunnerProfileButton,
        Button saveRemoteRunnerProfileButton,
        Button deleteRemoteRunnerProfileButton,
        Button selectRemoteRunnerProfileButton,
        Button testRemoteRunnerProfileButton)
    {
        var grid = MainWindowElementFactory.CreateTwoColumnGrid();
        grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Remote",
            "Connection profile",
            "Configure remote SSH/direct connectivity and path mapping used by remote-managed workflows.",
            MainWindowElementFactory.BoundTextBox("Profile label", "Connection.ProfileLabel"),
            MainWindowElementFactory.LabeledField("Connection mode",
                MainWindowElementFactory.BoundComboBox("Connection.ConnectionModes", "Connection.ConnectionMode")),
            MainWindowElementFactory.BoundTextBox("Direct backend base URL", "Connection.DirectBackendBaseUrl"),
            MainWindowElementFactory.BoundTextBox("Remote host", "Connection.RemoteHost"),
            MainWindowElementFactory.BoundTextBox("Remote SSH port", "Connection.RemoteSshPort"),
            MainWindowElementFactory.BoundTextBox("Remote user", "Connection.RemoteUser"),
            MainWindowElementFactory.BoundTextBox("SSH key path", "Connection.SshKeyPath"),
            MainWindowElementFactory.BoundTextBox("Remote workspace root", "Connection.RemoteWorkspaceRoot"),
            MainWindowElementFactory.BoundTextBox("Remote repo path", "Connection.RemoteRepoPath"),
            MainWindowElementFactory.BoundTextBox("Remote path prefix", "Connection.RemotePathPrefix"),
            saveConnectionButton,
            MainWindowElementFactory.BoundTextBlock("Connection.ModeSummary", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("Connection.TransportStatus", foreground: MainWindowElementFactory.TextSecondaryBrush)), 0, 0);

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Runner",
            "Runner profiles",
            "Create, test, select, and delete remote runner profiles for live testing scenarios.",
            remoteRunnerProfileListView,
            MainWindowElementFactory.HorizontalButtons(newRemoteRunnerProfileButton, saveRemoteRunnerProfileButton),
            MainWindowElementFactory.HorizontalButtons(deleteRemoteRunnerProfileButton, selectRemoteRunnerProfileButton, testRemoteRunnerProfileButton),
            MainWindowElementFactory.BoundTextBlock("RemoteRunners.StatusMessage", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("RemoteRunners.TestSummary", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBox("Profile id", "RemoteRunners.ProfileId"),
            MainWindowElementFactory.BoundTextBox("Display name", "RemoteRunners.DisplayName"),
            MainWindowElementFactory.BoundTextBox("Base URL", "RemoteRunners.BaseUrl"),
            MainWindowElementFactory.BoundTextBox("Transport mode", "RemoteRunners.TransportMode"),
            MainWindowElementFactory.BoundTextBox("SSH host", "RemoteRunners.SshHost"),
            MainWindowElementFactory.BoundTextBox("SSH port", "RemoteRunners.SshPort"),
            MainWindowElementFactory.BoundTextBox("SSH user", "RemoteRunners.SshUser"),
            MainWindowElementFactory.BoundTextBox("Runner token reference", "RemoteRunners.RunnerAuthTokenReference"),
            MainWindowElementFactory.BoundTextBox("Runner auth token", "RemoteRunners.RunnerAuthToken"),
            MainWindowElementFactory.BoundTextBox("Default scenario id", "RemoteRunners.DefaultScenarioId"),
            MainWindowElementFactory.BoundMultilineTextBox("Terminal command", "RemoteRunners.TerminalCommand", 90)), 0, 1);

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Testing",
            "Remote test execution",
            "Use selected runner profiles as defaults for desktop-started Codex sessions unless overridden in-session.",
            MainWindowElementFactory.BoundTextBlock("Connection.EffectiveBackendBaseUrl", foreground: MainWindowElementFactory.TextSecondaryBrush, mono: true),
            MainWindowElementFactory.BoundTextBlock("StatusStrip.StreamStatus", foreground: MainWindowElementFactory.TextSecondaryBrush)), 1, 0);

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Notes",
            "Remote operator notes",
            "Keep operational notes and forwarded user metadata aligned with remote boundaries.",
            MainWindowElementFactory.BoundCheckBox("Send forwarded user header", "Connection.SendForwardedUserHeader"),
            MainWindowElementFactory.BoundTextBox("Forwarded user header", "Connection.ForwardedUserHeaderName"),
            MainWindowElementFactory.BoundMultilineTextBox("Operator notes", "Connection.Notes", 120)), 1, 1);

        return MainWindowElementFactory.BuildPage(
            "Remote",
            "Remote and Runner Surface",
            "Manage remote transport settings and runner profiles used for remote testing and computer-use operations.",
            grid);
    }

    internal static FrameworkElement BuildSettingsPage(
        Button useDetectedCodexSetupButton,
        Button refreshCodexStatusButton,
        Button startChatGptCodexLoginButton,
        Button refreshMcpPolicyButton,
        Button saveGlobalMcpPolicyButton,
        Button saveRepoMcpPolicyButton,
        Button addGlobalServerPolicyButton,
        Button removeGlobalServerPolicyButton,
        Button addRepoServerPolicyButton,
        Button removeRepoServerPolicyButton)
    {
        var grid = MainWindowElementFactory.CreateTwoColumnGrid();
        grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        MainWindowElementFactory.AddToGrid(grid, BuildCodexCard(
            useDetectedCodexSetupButton,
            refreshCodexStatusButton,
            startChatGptCodexLoginButton), 0, 0);

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "MCP Policy",
            "Global and repo policy scopes",
            "Edit global and repo policy scopes, including harness DI/language options, then inspect merged effective policy preview.",
            MainWindowElementFactory.BoundTextBox("Repo scope key", "McpPolicy.RepoScopeKey"),
            MainWindowElementFactory.HorizontalButtons(refreshMcpPolicyButton, saveGlobalMcpPolicyButton, saveRepoMcpPolicyButton),
            MainWindowElementFactory.BoundTextBlock("McpPolicy.StatusMessage", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.Label("Global Harness Preferences", MainWindowElementFactory.SemiBoldWeight, 15),
            MainWindowElementFactory.LabeledField(
                "Global DI preset",
                MainWindowElementFactory.BoundComboBox("McpPolicy.DiPresetOptions", "McpPolicy.GlobalDiPreset")),
            MainWindowElementFactory.LabeledField(
                "Global language preset",
                MainWindowElementFactory.BoundComboBox("McpPolicy.LanguagePresetOptions", "McpPolicy.GlobalLanguagePreset")),
            MainWindowElementFactory.BoundTextBox("Global custom DI descriptor", "McpPolicy.GlobalCustomDiDescriptor"),
            MainWindowElementFactory.BoundTextBox("Global internal concurrency cap (0 = unlimited)", "McpPolicy.GlobalInternalConcurrencyCap"),
            MainWindowElementFactory.BoundTextBox("Global downstream concurrency cap (0 = unlimited)", "McpPolicy.GlobalDownstreamConcurrencyCap"),
            MainWindowElementFactory.LabeledField(
                "Global downstream MCP mode",
                MainWindowElementFactory.BoundComboBox("McpPolicy.DownstreamMcpModeOptions", "McpPolicy.GlobalDownstreamMcpMode")),
            MainWindowElementFactory.Label("Repo Harness Overrides", MainWindowElementFactory.SemiBoldWeight, 15),
            MainWindowElementFactory.LabeledField(
                "Repo DI preset",
                MainWindowElementFactory.BoundComboBox("McpPolicy.DiPresetOptions", "McpPolicy.RepoDiPreset")),
            MainWindowElementFactory.LabeledField(
                "Repo language preset",
                MainWindowElementFactory.BoundComboBox("McpPolicy.LanguagePresetOptions", "McpPolicy.RepoLanguagePreset")),
            MainWindowElementFactory.BoundTextBox("Repo custom DI descriptor", "McpPolicy.RepoCustomDiDescriptor"),
            MainWindowElementFactory.BoundTextBox("Repo internal concurrency cap (blank = inherit)", "McpPolicy.RepoInternalConcurrencyCap"),
            MainWindowElementFactory.BoundTextBox("Repo downstream concurrency cap (blank = inherit)", "McpPolicy.RepoDownstreamConcurrencyCap"),
            MainWindowElementFactory.LabeledField(
                "Repo downstream MCP mode (blank = inherit)",
                MainWindowElementFactory.BoundComboBox("McpPolicy.DownstreamMcpModeOptions", "McpPolicy.RepoDownstreamMcpMode")),
            MainWindowElementFactory.Label("Effective Harness Preferences", MainWindowElementFactory.SemiBoldWeight, 15),
            MainWindowElementFactory.ReadOnlyBoundValue("Effective DI preset", "McpPolicy.EffectiveDiPreset"),
            MainWindowElementFactory.ReadOnlyBoundValue("Effective language preset", "McpPolicy.EffectiveLanguagePreset"),
            MainWindowElementFactory.ReadOnlyBoundValue("Effective custom DI descriptor", "McpPolicy.EffectiveCustomDiDescriptor"),
            MainWindowElementFactory.ReadOnlyBoundValue("Effective internal concurrency cap", "McpPolicy.EffectiveInternalConcurrencyCap"),
            MainWindowElementFactory.ReadOnlyBoundValue("Effective downstream concurrency cap", "McpPolicy.EffectiveDownstreamConcurrencyCap"),
            MainWindowElementFactory.ReadOnlyBoundValue("Effective downstream MCP mode", "McpPolicy.EffectiveDownstreamMcpMode"),
            MainWindowElementFactory.Label("Global MCP Servers", MainWindowElementFactory.SemiBoldWeight, 15),
            MainWindowElementFactory.BoundTextBox("Add global server", "McpPolicy.NewGlobalServerName"),
            MainWindowElementFactory.HorizontalButtons(addGlobalServerPolicyButton, removeGlobalServerPolicyButton),
            MainWindowElementFactory.BoundSelectableListView(
                "McpPolicy.GlobalServerPolicies",
                "McpPolicy.SelectedGlobalServerPolicy",
                160,
                displayMemberPath: "DisplaySummary"),
            MainWindowElementFactory.BoundTextBox("Server name", "McpPolicy.SelectedGlobalServerPolicy.ServerName"),
            MainWindowElementFactory.BoundCheckBox("Enabled", "McpPolicy.SelectedGlobalServerPolicy.Enabled"),
            MainWindowElementFactory.LabeledField(
                "Mode",
                MainWindowElementFactory.BoundComboBox("McpPolicy.ServerModeOptions", "McpPolicy.SelectedGlobalServerPolicy.Mode")),
            MainWindowElementFactory.BoundMultilineTextBox("Env overrides (JSON)", "McpPolicy.SelectedGlobalServerPolicy.EnvJson", 90),
            MainWindowElementFactory.Label("Repo MCP Overrides", MainWindowElementFactory.SemiBoldWeight, 15),
            MainWindowElementFactory.BoundTextBox("Add repo server", "McpPolicy.NewRepoServerName"),
            MainWindowElementFactory.HorizontalButtons(addRepoServerPolicyButton, removeRepoServerPolicyButton),
            MainWindowElementFactory.BoundSelectableListView(
                "McpPolicy.RepoServerPolicies",
                "McpPolicy.SelectedRepoServerPolicy",
                160,
                displayMemberPath: "DisplaySummary"),
            MainWindowElementFactory.BoundTextBox("Server name", "McpPolicy.SelectedRepoServerPolicy.ServerName"),
            MainWindowElementFactory.BoundCheckBox("Enabled", "McpPolicy.SelectedRepoServerPolicy.Enabled"),
            MainWindowElementFactory.LabeledField(
                "Mode",
                MainWindowElementFactory.BoundComboBox("McpPolicy.ServerModeOptions", "McpPolicy.SelectedRepoServerPolicy.Mode")),
            MainWindowElementFactory.BoundMultilineTextBox("Env overrides (JSON)", "McpPolicy.SelectedRepoServerPolicy.EnvJson", 90),
            MainWindowElementFactory.BoundMultilineTextBox("Global policy JSON", "McpPolicy.GlobalPolicyJson", 170),
            MainWindowElementFactory.BoundMultilineTextBox("Repo policy JSON", "McpPolicy.RepoPolicyJson", 170),
            MainWindowElementFactory.BoundMultilineTextBox("Merged preview", "McpPolicy.MergedPreviewJson", 170)), 0, 1);

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Defaults",
            "Session defaults",
            "Connection and workspace defaults feed launch/runtime envelopes for local and remote managed sessions.",
            MainWindowElementFactory.BoundTextBox("Preferred profile key", "Connection.PreferredProfileKey"),
            MainWindowElementFactory.BoundTextBox("Preferred model", "Connection.PreferredModel"),
            MainWindowElementFactory.BoundTextBox("Preferred reasoning effort", "Connection.PreferredReasoningEffort"),
            MainWindowElementFactory.BoundTextBox("Session list limit", "Connection.SessionListLimit"),
            MainWindowElementFactory.BoundTextBox("Event replay limit", "Connection.EventReplayLimit")), 1, 0);

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Catalog",
            "Operation catalog status",
            "Operations are discovered from backend catalogs with compatibility fallback defaults.",
            MainWindowElementFactory.BoundTextBlock("Operations.StatusMessage", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("RemoteRunners.StatusMessage", foreground: MainWindowElementFactory.TextSecondaryBrush)), 1, 1);

        return MainWindowElementFactory.BuildPage(
            "Settings",
            "Configuration Surface",
            "Configure Codex setup, MCP policy scopes, and session defaults used across all runtime modes.",
            grid);
    }

    private static Border BuildCodexCard(
        Button useDetectedCodexSetupButton,
        Button refreshCodexStatusButton,
        Button startChatGptCodexLoginButton)
        => MainWindowElementFactory.BuildContentCard(
            "Codex",
            "Installed app-server and login reuse",
            "Point local runtime launches at the existing Codex executable and CODEX_HOME so desktop reuses your ChatGPT login and profile defaults.",
            MainWindowElementFactory.BoundTextBox("Codex executable", "Codex.ExecutablePath"),
            MainWindowElementFactory.BoundTextBox("Codex home", "Codex.CodexHomePath"),
            MainWindowElementFactory.HorizontalButtons(
                useDetectedCodexSetupButton,
                refreshCodexStatusButton,
                startChatGptCodexLoginButton),
            MainWindowElementFactory.ReadOnlyBoundValue("Config file", "Codex.ConfigFilePath"),
            MainWindowElementFactory.ReadOnlyBoundValue("Auth file", "Codex.AuthFilePath"),
            MainWindowElementFactory.ReadOnlyBoundValue("Auth mode", "Codex.AuthMode"),
            MainWindowElementFactory.ReadOnlyBoundValue("Login status", "Codex.LoginStatus"),
            MainWindowElementFactory.BoundTextBlock("Codex.Summary", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("Codex.StatusMessage", foreground: MainWindowElementFactory.TextSecondaryBrush));
}
