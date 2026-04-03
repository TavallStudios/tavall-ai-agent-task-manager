using AgentTaskManager.Desktop.Contracts;
using AgentTaskManager.Desktop.Services;
using CommunityToolkit.Mvvm.ComponentModel;

namespace AgentTaskManager.Desktop.ViewModels;

public sealed class CodexSettingsViewModel : ObservableObject
{
    private readonly IDesktopConnectionSettingsService _connectionSettingsService;
    private readonly ICodexEnvironmentService _codexEnvironmentService;
    private bool _initialized;
    private string _executablePath = string.Empty;
    private string _codexHomePath = string.Empty;
    private string _configFilePath = string.Empty;
    private string _authFilePath = string.Empty;
    private string _authMode = "unknown";
    private string _loginStatus = "Codex login status unavailable.";
    private string _summary = "The desktop runtime can reuse an installed local Codex setup.";
    private string _statusMessage = "Refresh status to detect the local Codex installation and login state.";
    private bool _isAuthenticated;
    private bool _usesChatGpt;
    private bool _isBusy;

    public CodexSettingsViewModel(
        IDesktopConnectionSettingsService connectionSettingsService,
        ICodexEnvironmentService codexEnvironmentService)
    {
        _connectionSettingsService = connectionSettingsService;
        _codexEnvironmentService = codexEnvironmentService;
    }

    public string ExecutablePath
    {
        get => _executablePath;
        set => SetProperty(ref _executablePath, value);
    }

    public string CodexHomePath
    {
        get => _codexHomePath;
        set => SetProperty(ref _codexHomePath, value);
    }

    public string ConfigFilePath
    {
        get => _configFilePath;
        private set => SetProperty(ref _configFilePath, value);
    }

    public string AuthFilePath
    {
        get => _authFilePath;
        private set => SetProperty(ref _authFilePath, value);
    }

    public string AuthMode
    {
        get => _authMode;
        private set => SetProperty(ref _authMode, value);
    }

    public string LoginStatus
    {
        get => _loginStatus;
        private set => SetProperty(ref _loginStatus, value);
    }

    public string Summary
    {
        get => _summary;
        private set => SetProperty(ref _summary, value);
    }

    public string StatusMessage
    {
        get => _statusMessage;
        set => SetProperty(ref _statusMessage, value);
    }

    public bool IsAuthenticated
    {
        get => _isAuthenticated;
        private set => SetProperty(ref _isAuthenticated, value);
    }

    public bool UsesChatGpt
    {
        get => _usesChatGpt;
        private set => SetProperty(ref _usesChatGpt, value);
    }

    public bool IsBusy
    {
        get => _isBusy;
        private set => SetProperty(ref _isBusy, value);
    }

    public async Task InitializeAsync(CancellationToken cancellationToken)
    {
        if (_initialized)
        {
            return;
        }

        DesktopConnectionSettingsDto settings = _connectionSettingsService.Current;
        LoadFromSettings(settings);
        CodexLocalSetupDto setup = await ResolveSetupAsync(
            NormalizeOptionalPath(settings.CodexExecutablePath),
            NormalizeOptionalPath(settings.CodexHomePath),
            cancellationToken);
        ApplySetup(setup);
        if (string.IsNullOrWhiteSpace(settings.CodexExecutablePath)
            || string.IsNullOrWhiteSpace(settings.CodexHomePath))
        {
            await PersistAsync(setup, cancellationToken);
        }

        _initialized = true;
    }

    public async Task SaveAsync(CancellationToken cancellationToken)
    {
        CodexLocalSetupDto setup = await ResolveSetupAsync(
            NormalizeOptionalPath(ExecutablePath),
            NormalizeOptionalPath(CodexHomePath),
            cancellationToken);
        await PersistAsync(setup, cancellationToken);
        ApplySetup(setup);
    }

    public async Task UseDetectedSetupAsync(CancellationToken cancellationToken)
    {
        CodexLocalSetupDto setup = await ResolveSetupAsync(null, null, cancellationToken);
        await PersistAsync(setup, cancellationToken);
        ApplySetup(setup);
    }

    public async Task RefreshStatusAsync(CancellationToken cancellationToken)
        => ApplySetup(await ResolveSetupAsync(
            NormalizeOptionalPath(ExecutablePath),
            NormalizeOptionalPath(CodexHomePath),
            cancellationToken));

    public async Task StartChatGptSignInAsync(CancellationToken cancellationToken)
    {
        CodexLocalSetupDto setup = await ResolveSetupAsync(
            NormalizeOptionalPath(ExecutablePath),
            NormalizeOptionalPath(CodexHomePath),
            cancellationToken);
        ApplySetup(setup);
        if (setup.UsesChatGpt && setup.IsAuthenticated)
        {
            StatusMessage = "Codex is already authenticated with ChatGPT.";
            DesktopDiagnosticsLog.WriteInfo("Codex ChatGPT login already active.");
            return;
        }

        await _codexEnvironmentService.LaunchChatGptLoginAsync(
            setup.ExecutablePath,
            setup.CodexHomePath,
            cancellationToken);
        DesktopDiagnosticsLog.WriteInfo($"Opened Codex ChatGPT login flow using CODEX_HOME={setup.CodexHomePath}.");
        StatusMessage = $"Opened the Codex ChatGPT login flow using CODEX_HOME={setup.CodexHomePath}. Refresh status after the login finishes.";
    }

    private async Task<CodexLocalSetupDto> ResolveSetupAsync(
        string? preferredExecutablePath,
        string? preferredCodexHomePath,
        CancellationToken cancellationToken)
    {
        IsBusy = true;
        try
        {
            return await _codexEnvironmentService.ResolveSetupAsync(
                preferredExecutablePath,
                preferredCodexHomePath,
                cancellationToken);
        }
        finally
        {
            IsBusy = false;
        }
    }

    private async Task PersistAsync(CodexLocalSetupDto setup, CancellationToken cancellationToken)
    {
        await _connectionSettingsService.SaveAsync(
            _connectionSettingsService.Current with
            {
                CodexExecutablePath = setup.ExecutablePath,
                CodexHomePath = setup.CodexHomePath
            },
            cancellationToken);
    }

    private void LoadFromSettings(DesktopConnectionSettingsDto settings)
    {
        ExecutablePath = settings.CodexExecutablePath;
        CodexHomePath = settings.CodexHomePath;
    }

    private void ApplySetup(CodexLocalSetupDto setup)
    {
        ExecutablePath = setup.ExecutablePath;
        CodexHomePath = setup.CodexHomePath;
        ConfigFilePath = setup.ConfigFilePath;
        AuthFilePath = setup.AuthFilePath;
        AuthMode = setup.AuthMode;
        LoginStatus = setup.LoginStatus;
        Summary = setup.Summary;
        IsAuthenticated = setup.IsAuthenticated;
        UsesChatGpt = setup.UsesChatGpt;
        StatusMessage = BuildStatusMessage(setup);
        DesktopDiagnosticsLog.WriteInfo(
            $"Codex setup resolved. Executable='{setup.ExecutablePath}', Home='{setup.CodexHomePath}', AuthMode='{setup.AuthMode}', UsesChatGpt={setup.UsesChatGpt}, IsAuthenticated={setup.IsAuthenticated}.");
    }

    private static string BuildStatusMessage(CodexLocalSetupDto setup)
    {
        if (setup.UsesChatGpt && setup.IsAuthenticated)
        {
            return "Local Codex app-server launches will reuse the existing ChatGPT-backed login on this machine.";
        }

        if (setup.IsAuthenticated)
        {
            return $"Local Codex app-server launches will reuse the current {setup.AuthMode} login.";
        }

        return "Codex is installed, but no active ChatGPT login was detected. Start the ChatGPT sign-in flow before launching a local runtime.";
    }

    private static string? NormalizeOptionalPath(string value)
        => string.IsNullOrWhiteSpace(value) ? null : value.Trim();
}
