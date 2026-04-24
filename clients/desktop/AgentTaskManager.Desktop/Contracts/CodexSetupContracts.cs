namespace AgentTaskManager.Desktop.Contracts;

public sealed record CodexLocalSetupDto(
    string ExecutablePath,
    string CodexHomePath,
    string ConfigFilePath,
    string AuthFilePath,
    string AuthMode,
    string LoginStatus,
    bool IsAuthenticated,
    bool UsesChatGpt,
    string Summary);
