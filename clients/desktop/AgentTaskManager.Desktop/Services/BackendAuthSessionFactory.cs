using AgentTaskManager.Desktop.Contracts;

namespace AgentTaskManager.Desktop.Services;

internal static class BackendAuthSessionFactory
{
    public static BackendAuthSessionDto CreateBasicSession(
        Uri backendBaseUri,
        string userName,
        DesktopConnectionSettingsDto settings)
        => new(
            backendBaseUri.ToString().TrimEnd('/'),
            userName,
            DesktopAuthModes.Basic,
            string.Empty,
            string.Empty,
            DateTimeOffset.MaxValue,
            userName,
            userName,
            RequiresCodexLogin: false,
            settings.RuntimeMode,
            RemoteContinuationEnabled: settings.RuntimeMode != DesktopRuntimeModes.LocalManaged);
}
