using AgentTaskManager.Desktop.Contracts;
using CommunityToolkit.Mvvm.ComponentModel;

namespace AgentTaskManager.Desktop.ViewModels;

public sealed class SignInViewModel : ObservableObject
{
    private string _backendUrl = "http://127.0.0.1:9000";
    private string _userName = Environment.UserName;
    private string _displayName = "Signed out";
    private string _statusMessage = "Sign in to AgentTaskManager.";
    private bool _isSignedIn;
    private bool _isBusy;
    private bool _requiresCodexLogin;

    public string BackendUrl
    {
        get => _backendUrl;
        set => SetProperty(ref _backendUrl, value);
    }

    public string UserName
    {
        get => _userName;
        set => SetProperty(ref _userName, value);
    }

    public string DisplayName
    {
        get => _displayName;
        set => SetProperty(ref _displayName, value);
    }

    public string StatusMessage
    {
        get => _statusMessage;
        set => SetProperty(ref _statusMessage, value);
    }

    public bool IsSignedIn
    {
        get => _isSignedIn;
        set => SetProperty(ref _isSignedIn, value);
    }

    public bool IsBusy
    {
        get => _isBusy;
        set => SetProperty(ref _isBusy, value);
    }

    public void ApplySession(BackendAuthSessionDto session)
    {
        BackendUrl = session.BackendBaseUrl;
        UserName = session.UserName;
        DisplayName = session.DisplayName;
        IsSignedIn = true;
        _requiresCodexLogin = session.RequiresCodexLogin;
        StatusMessage = BuildHealthyStatus();
    }

    public void MarkBackendHealthy()
    {
        if (!IsSignedIn)
        {
            return;
        }

        StatusMessage = BuildHealthyStatus();
    }

    public void MarkSignedOut(string message)
    {
        DisplayName = "Signed out";
        IsSignedIn = false;
        IsBusy = false;
        _requiresCodexLogin = false;
        StatusMessage = message;
    }

    private string BuildHealthyStatus()
        => _requiresCodexLogin
            ? "Backend auth is active. Codex/OpenAI login still needs confirmation from the runtime."
            : "Backend auth is active.";
}
