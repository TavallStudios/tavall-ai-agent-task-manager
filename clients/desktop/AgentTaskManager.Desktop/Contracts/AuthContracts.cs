namespace AgentTaskManager.Desktop.Contracts;

public sealed record BackendSignInRequestDto(
    string UserName,
    string Password,
    string DeviceId,
    string ClientName,
    string ClientVersion);

public sealed record BackendRefreshRequestDto(
    string RefreshToken,
    string DeviceId,
    string ClientName,
    string ClientVersion);

public sealed record BackendSignInResponseDto(
    string AccessToken,
    string RefreshToken,
    DateTimeOffset AccessTokenExpiresAt,
    string UserId,
    string DisplayName,
    bool RequiresCodexLogin,
    string CodexAuthMode,
    bool RemoteContinuationEnabled);

public sealed record BackendAuthSessionDto(
    string BackendBaseUrl,
    string UserName,
    string AuthMode,
    string AccessToken,
    string RefreshToken,
    DateTimeOffset AccessTokenExpiresAt,
    string UserId,
    string DisplayName,
    bool RequiresCodexLogin,
    string CodexAuthMode,
    bool RemoteContinuationEnabled);
