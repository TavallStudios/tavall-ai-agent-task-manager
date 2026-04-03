using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace AgentTaskManager.Desktop;

internal static class MainWindowSectionFactory
{
    internal static FrameworkElement BuildAccessPage(
        PasswordBox backendPasswordBox,
        Button signInButton,
        Button signOutButton,
        Button saveConnectionButton,
        Button useDetectedCodexSetupButton,
        Button refreshCodexStatusButton,
        Button startChatGptCodexLoginButton,
        Button createSessionButton,
        Button openWorkspaceButton)
    {
        var overviewGrid = MainWindowElementFactory.CreateTwoColumnGrid();
        overviewGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        MainWindowElementFactory.AddToGrid(overviewGrid, MainWindowElementFactory.BuildContentCard(
            "Environment",
            "Backend routing",
            "The desktop now treats the selected environment as the source of truth for auth, sessions, and runtime control.",
            MainWindowElementFactory.BoundTextBlock("Connection.ModeSummary", fontWeight: MainWindowElementFactory.SemiBoldWeight),
            MainWindowElementFactory.ReadOnlyBoundValue("Effective backend", "Connection.EffectiveBackendBaseUrl"),
            MainWindowElementFactory.ReadOnlyBoundValue("Transport state", "Connection.TransportStatus"),
            MainWindowElementFactory.ReadOnlyBoundValue("Profile", "Connection.ProfileLabel")), 0, 0);
        MainWindowElementFactory.AddToGrid(overviewGrid, MainWindowElementFactory.BuildContentCard(
            "Identity",
            "Backend sign-in",
            "Use the current connection target. Change the backend in the connection profile or the sidebar environment switcher.",
            MainWindowElementFactory.ReadOnlyBoundValue("Backend target", "SignIn.BackendUrl"),
            MainWindowElementFactory.BoundTextBox("User name", "SignIn.UserName"),
            MainWindowElementFactory.LabeledField("Password", backendPasswordBox),
            MainWindowElementFactory.HorizontalButtons(signInButton, signOutButton),
            MainWindowElementFactory.BoundTextBlock("SignIn.DisplayName", fontWeight: MainWindowElementFactory.SemiBoldWeight),
            MainWindowElementFactory.BoundTextBlock("SignIn.StatusMessage", foreground: MainWindowElementFactory.TextSecondaryBrush)), 0, 1);

        var configurationGrid = MainWindowElementFactory.CreateTwoColumnGrid();
        configurationGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        configurationGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        configurationGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        MainWindowElementFactory.AddToGrid(configurationGrid, MainWindowElementFactory.BuildContentCard(
            "Connection",
            "Profile and transport",
            "Tune direct URLs, remote SSH details, and the tunnel path that feeds the selected environment.",
            MainWindowElementFactory.BoundTextBox("Profile label", "Connection.ProfileLabel"),
            MainWindowElementFactory.LabeledField("Connection mode",
                MainWindowElementFactory.BoundComboBox("Connection.ConnectionModes", "Connection.ConnectionMode")),
            MainWindowElementFactory.LabeledField("Auth mode",
                MainWindowElementFactory.BoundComboBox("Connection.AuthModes", "Connection.AuthMode")),
            MainWindowElementFactory.BoundTextBox("Direct backend base URL", "Connection.DirectBackendBaseUrl"),
            MainWindowElementFactory.BoundTextBox("Remote host", "Connection.RemoteHost"),
            MainWindowElementFactory.BoundTextBox("Remote SSH port", "Connection.RemoteSshPort"),
            MainWindowElementFactory.BoundTextBox("Remote user", "Connection.RemoteUser"),
            MainWindowElementFactory.BoundTextBox("SSH key path", "Connection.SshKeyPath"),
            MainWindowElementFactory.BoundTextBox("Local tunnel port", "Connection.LocalTunnelPort"),
            MainWindowElementFactory.BoundTextBox("Remote backend port", "Connection.RemoteBackendPort"),
            MainWindowElementFactory.BoundTextBox("Tunnel timeout seconds", "Connection.TunnelConnectTimeoutSeconds"),
            MainWindowElementFactory.BoundCheckBox("Auto-start SSH tunnel on demand", "Connection.AutoStartTunnel"),
            saveConnectionButton), 0, 0);
        MainWindowElementFactory.AddToGrid(configurationGrid, MainWindowElementFactory.BuildContentCard(
            "Runtime",
            "Execution defaults",
            "Control whether the desktop manages Codex locally, hands work to the backend, or only observes remote activity.",
            MainWindowElementFactory.LabeledField("Runtime mode",
                MainWindowElementFactory.BoundComboBox("Connection.RuntimeModes", "Connection.RuntimeMode")),
            MainWindowElementFactory.BoundCheckBox("Allow runtime handoff", "Connection.AllowRuntimeHandoff"),
            MainWindowElementFactory.BoundCheckBox("Create runtime by default", "Connection.CreateRuntimeByDefault"),
            MainWindowElementFactory.BoundCheckBox("Include local workspace catalog", "Connection.IncludeLocalWorkspaceCatalog"),
            MainWindowElementFactory.BoundTextBox("Session list limit", "Connection.SessionListLimit"),
            MainWindowElementFactory.BoundTextBox("Event replay limit", "Connection.EventReplayLimit"),
            MainWindowElementFactory.BoundTextBox("Preferred profile key", "Connection.PreferredProfileKey"),
            MainWindowElementFactory.BoundTextBox("Preferred model", "Connection.PreferredModel"),
            MainWindowElementFactory.BoundTextBox("Preferred reasoning effort", "Connection.PreferredReasoningEffort")), 0, 1);
        MainWindowElementFactory.AddToGrid(configurationGrid, MainWindowElementFactory.BuildContentCard(
            "Workspace",
            "Path mapping",
            "Keep remote and local paths aligned so open-workspace and file navigation resolve correctly in either backend mode.",
            MainWindowElementFactory.BoundTextBox("Remote workspace root", "Connection.RemoteWorkspaceRoot"),
            MainWindowElementFactory.BoundTextBox("Remote repo path", "Connection.RemoteRepoPath"),
            MainWindowElementFactory.BoundTextBox("Remote path prefix", "Connection.RemotePathPrefix"),
            MainWindowElementFactory.BoundTextBox("Local path prefix", "Connection.LocalPathPrefix"),
            MainWindowElementFactory.BoundCheckBox("Send forwarded user header", "Connection.SendForwardedUserHeader"),
            MainWindowElementFactory.BoundTextBox("Forwarded user header", "Connection.ForwardedUserHeaderName"),
            MainWindowElementFactory.BoundMultilineTextBox("Operator notes", "Connection.Notes", 110)), 1, 0);
        MainWindowElementFactory.AddToGrid(configurationGrid, MainWindowElementFactory.BuildContentCard(
            "Launch",
            "Session creation",
            "Create custom desktop sessions while preserving the Codex-style operator shell for output review and diagnostics.",
            MainWindowElementFactory.LabeledField(
                "Workspace",
                MainWindowElementFactory.BoundComboBox(
                    "WorkspacePicker.Workspaces",
                    "WorkspacePicker.SelectedWorkspace",
                    displayMemberPath: "DisplaySummary")),
            MainWindowElementFactory.BoundTextBox("Session title", "WorkspacePicker.SessionTitle"),
            MainWindowElementFactory.BoundTextBox("Project key", "WorkspacePicker.ProjectKey"),
            MainWindowElementFactory.BoundTextBox("Workspace root", "WorkspacePicker.WorkspaceRoot"),
            MainWindowElementFactory.BoundTextBox("Repo path", "WorkspacePicker.RepoPath"),
            MainWindowElementFactory.BoundTextBox("Profile key", "WorkspacePicker.ProfileKey"),
            MainWindowElementFactory.BoundTextBox("Workspace scope", "WorkspacePicker.WorkspaceScope"),
            MainWindowElementFactory.BoundCheckBox("Utility session", "WorkspacePicker.UtilitySession"),
            MainWindowElementFactory.BoundCheckBox("Create runtime immediately", "WorkspacePicker.CreateRuntime"),
            MainWindowElementFactory.BoundMultilineTextBox("Initial prompt", "WorkspacePicker.InitialPrompt", 100),
            MainWindowElementFactory.HorizontalButtons(createSessionButton, openWorkspaceButton)), 1, 1);
        Border codexCard = BuildCodexCard(
            useDetectedCodexSetupButton,
            refreshCodexStatusButton,
            startChatGptCodexLoginButton);
        Grid.SetRow(codexCard, 2);
        Grid.SetColumn(codexCard, 0);
        Grid.SetColumnSpan(codexCard, 2);
        configurationGrid.Children.Add(codexCard);

        return MainWindowElementFactory.BuildPage(
            "Access",
            "Backend control and session launch",
            "Switch between local and remote backends without stale auth state, then sign in and launch workspaces from the same operator surface.",
            overviewGrid,
            configurationGrid);
    }

    private static Border BuildCodexCard(
        Button useDetectedCodexSetupButton,
        Button refreshCodexStatusButton,
        Button startChatGptCodexLoginButton)
        => MainWindowElementFactory.BuildContentCard(
            "Codex",
            "Installed app-server and login reuse",
            "Point local runtime launches at the existing Codex executable and CODEX_HOME on this machine so the desktop reuses your ChatGPT login, config, MCP servers, and profile defaults.",
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

    internal static FrameworkElement BuildSessionsPage(
        ListView sessionListView,
        Button resumeSelectedSessionButton,
        Button submitTurnButton)
    {
        var grid = MainWindowElementFactory.CreateTwoColumnGrid();
        grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Sessions",
            "Recent sessions",
            "Attach to existing work, reload persisted state, or resume the selected remote session.",
            MainWindowElementFactory.BoundTextBlock("SessionList.StatusMessage", foreground: MainWindowElementFactory.TextSecondaryBrush),
            resumeSelectedSessionButton,
            sessionListView), 0, 0);
        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Context",
            "Timeline and turns",
            "Inspect the selected session state before you resume control or submit a follow-up turn.",
            MainWindowElementFactory.BoundTextBlock("SessionDetail.SessionTitle", fontWeight: MainWindowElementFactory.SemiBoldWeight),
            MainWindowElementFactory.BoundTextBlock("SessionDetail.WorkspaceRoot", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("SessionDetail.RuntimeBanner", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundListView("SessionDetail.Turns", minHeight: 220, displayMemberPath: "DisplaySummary")), 0, 1);
        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Events",
            "Live event stream",
            "Recent runtime and verifier events stream into this panel with backend refreshes when the state changes.",
            MainWindowElementFactory.BoundListView("SessionDetail.Events", minHeight: 260, displayMemberPath: "DisplaySummary")), 1, 0);
        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Turn",
            "Composer",
            "Submit the next instruction to the selected session from the desktop shell.",
            MainWindowElementFactory.BoundMultilineBodyTextBox("SessionDetail.PendingPrompt", 220),
            submitTurnButton), 1, 1);
        return MainWindowElementFactory.BuildPage(
            "Sessions",
            "Live session control",
            "Navigate active sessions, inspect the current timeline, and send new turns without leaving the operator shell.",
            grid);
    }

    internal static FrameworkElement BuildOutputPage()
    {
        var grid = MainWindowElementFactory.CreateTwoColumnGrid();
        grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

        Border outputGate = MainWindowElementFactory.BuildContentCard(
            "Output",
            "Candidate versus approved",
            "Separate the working candidate from the approved release so review stays fail-closed.",
            MainWindowElementFactory.BoundTextBlock("SessionDetail.OutputGate.StatusSummary", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.Label("Candidate", MainWindowElementFactory.SemiBoldWeight, 16),
            MainWindowElementFactory.BoundTextBlock("SessionDetail.OutputGate.CandidateSummary", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundReadOnlyBodyTextBox("SessionDetail.OutputGate.CandidateContent", 220),
            MainWindowElementFactory.Label("Approved", MainWindowElementFactory.SemiBoldWeight, 16),
            MainWindowElementFactory.BoundTextBlock("SessionDetail.OutputGate.ApprovedSummary", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundReadOnlyBodyTextBox("SessionDetail.OutputGate.ApprovedContent", 220));
        Grid.SetRow(outputGate, 0);
        Grid.SetColumn(outputGate, 0);
        Grid.SetRowSpan(outputGate, 2);
        grid.Children.Add(outputGate);

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Verification",
            "Verifier and receipts",
            "Track verifier verdicts and tool receipts beside the output gate so release decisions stay grounded in evidence.",
            MainWindowElementFactory.BoundTextBlock("SessionDetail.VerifierBanner", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("SessionDetail.ReceiptBanner", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundListView("SessionDetail.VerifierResults", maxHeight: 220, displayMemberPath: "DisplaySummary"),
            MainWindowElementFactory.BoundListView("SessionDetail.ToolReceipts", maxHeight: 220, displayMemberPath: "DisplaySummary")), 0, 1);
        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Memory",
            "Memory and devices",
            "Inspect memory references and attached devices that influenced the current session state.",
            MainWindowElementFactory.BoundTextBlock("SessionDetail.MemoryBanner", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundListView("SessionDetail.MemoryReferences", maxHeight: 220, displayMemberPath: "DisplaySummary"),
            MainWindowElementFactory.BoundListView("SessionDetail.Devices", maxHeight: 200, displayMemberPath: "DisplaySummary")), 1, 1);
        return MainWindowElementFactory.BuildPage(
            "Output",
            "Review gate",
            "Read the session output like a SaaS control plane: gated content on the left, verification evidence on the right.",
            grid);
    }

    internal static FrameworkElement BuildReviewPage(
        Button openSelectedPatchButton,
        Button openSelectedFileButton)
    {
        var grid = MainWindowElementFactory.CreateTwoColumnGrid();
        grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Review",
            "Patch review and file focus",
            "Inspect generated patches and navigation requests without leaving the desktop control surface.",
            MainWindowElementFactory.BoundTextBlock("SessionDetail.PatchReview.PatchSummary", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.HorizontalButtons(openSelectedPatchButton, openSelectedFileButton),
            MainWindowElementFactory.BoundSelectableListView(
                "SessionDetail.PatchReview.Patches",
                "SessionDetail.PatchReview.SelectedPatch",
                280,
                displayMemberPath: "DisplaySummary"),
            MainWindowElementFactory.BoundSelectableListView(
                "SessionDetail.PatchReview.FileFocusRequests",
                "SessionDetail.PatchReview.SelectedFileFocusRequest",
                240,
                displayMemberPath: "DisplaySummary")), 0, 0);
        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Preview",
            "Diff preview",
            "When patch artifacts exist, this panel renders the currently selected diff preview.",
            MainWindowElementFactory.BoundReadOnlyBodyTextBox("SessionDetail.PatchReview.SelectedDiffPreview", 560)), 0, 1);
        return MainWindowElementFactory.BuildPage(
            "Review",
            "Patch and file focus",
            "Keep custom review functions inside the Codex-style shell without flattening them into generic output panels.",
            grid);
    }

    internal static FrameworkElement BuildDiagnosticsPage()
    {
        var grid = MainWindowElementFactory.CreateTwoColumnGrid();
        grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Status",
            "Current session",
            "The active session summary is mirrored here for operational debugging and runtime observation.",
            MainWindowElementFactory.BoundTextBlock("SessionDetail.SessionTitle", fontWeight: MainWindowElementFactory.SemiBoldWeight),
            MainWindowElementFactory.BoundTextBlock("SessionDetail.WorkspaceRoot", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("SessionDetail.RuntimeBanner", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("SessionDetail.VerifierBanner", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("SessionDetail.ReceiptBanner", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("SessionDetail.MemoryBanner", foreground: MainWindowElementFactory.TextSecondaryBrush)), 0, 0);
        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Profile",
            "Connection diagnostics",
            "Confirm the selected backend, transport behavior, and operator notes before debugging remote failures.",
            MainWindowElementFactory.BoundTextBlock("Connection.ProfileLabel", fontWeight: MainWindowElementFactory.SemiBoldWeight),
            MainWindowElementFactory.BoundTextBlock("Connection.EffectiveBackendBaseUrl", foreground: MainWindowElementFactory.TextSecondaryBrush, mono: true),
            MainWindowElementFactory.BoundTextBlock("Connection.TransportStatus", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("Connection.Notes", foreground: MainWindowElementFactory.TextSecondaryBrush)), 0, 1);
        return MainWindowElementFactory.BuildPage(
            "Diagnostics",
            "Operational diagnostics",
            "Use this view to confirm connection state, session runtime state, and desktop-side operator context when behavior drifts.",
            grid);
    }
}
