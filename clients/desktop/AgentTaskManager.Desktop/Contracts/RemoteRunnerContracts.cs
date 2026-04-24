namespace AgentTaskManager.Desktop.Contracts;

public sealed record RemoteRunnerProfileDto(
    string ProfileId,
    string DisplayName,
    string BaseUrl,
    string TransportMode,
    string SshHost,
    int SshPort,
    string SshUser,
    string RunnerAuthTokenReference,
    string DefaultScenarioId,
    string TerminalCommand,
    bool Selected,
    DateTimeOffset UpdatedAt)
{
    public string DisplaySummary => $"{DisplayName}  [{TransportMode}]  {BaseUrl}";
}

public sealed record RemoteRunnerConnectionTestDto(
    bool Success,
    string Message,
    string HealthStatus,
    string CapabilitiesSummary,
    string EffectiveCommandPath);

