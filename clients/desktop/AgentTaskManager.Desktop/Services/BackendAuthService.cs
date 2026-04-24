using AgentTaskManager.Desktop.Contracts;
using System.Net.Http.Headers;
using System.Net.Http.Json;

namespace AgentTaskManager.Desktop.Services;

public sealed class BackendAuthService : IBackendAuthService
{
    private const string AccessTokenResource = "backend-access-token";
    private const string RefreshTokenResource = "backend-refresh-token";
    private const string BasicPasswordResource = "backend-basic-password";
    private const string SecretUser = "desktop";

    private readonly ISecureCredentialStorageService _secureCredentialStorageService;
    private readonly IDevicePresenceService _devicePresenceService;
    private readonly IDesktopConnectionSettingsService _connectionSettingsService;
    private readonly BackendAuthSessionStore _sessionStore;
    private string? _cachedCredentialSecret;

    public BackendAuthService(
        ISecureCredentialStorageService secureCredentialStorageService,
        IDevicePresenceService devicePresenceService,
        IDesktopConnectionSettingsService connectionSettingsService,
        BackendAuthSessionStore sessionStore)
    {
        _secureCredentialStorageService = secureCredentialStorageService;
        _devicePresenceService = devicePresenceService;
        _connectionSettingsService = connectionSettingsService;
        _sessionStore = sessionStore;
    }

    public BackendAuthSessionDto? CurrentSession { get; private set; }

    public Uri GetBackendBaseUri() => _connectionSettingsService.GetBackendBaseUri();

    public async Task<BackendAuthSessionDto?> TryRestoreAsync(CancellationToken cancellationToken)
    {
        CurrentSession = null;
        _cachedCredentialSecret = null;
        BackendStoredAuthMetadata? metadata = await _sessionStore.LoadAsync(cancellationToken);
        if (metadata == null)
        {
            return null;
        }

        return metadata.AuthMode == DesktopAuthModes.Basic
            ? await RestoreBasicSessionAsync(metadata, cancellationToken)
            : await RestoreTokenSessionAsync(metadata, cancellationToken);
    }

    public async Task<BackendAuthSessionDto> SignInAsync(
        Uri backendBaseUri,
        string userName,
        string password,
        CancellationToken cancellationToken)
    {
        backendBaseUri = EnsureBaseAddress(backendBaseUri);
        DesktopConnectionSettingsDto settings = _connectionSettingsService.Current;
        await _connectionSettingsService.EnsureBackendTransportAsync(cancellationToken);

        if (settings.AuthMode == DesktopAuthModes.Basic)
        {
            await ValidateBasicAsync(backendBaseUri, userName, password, settings, cancellationToken);
            CurrentSession = BackendAuthSessionFactory.CreateBasicSession(backendBaseUri, userName, settings);
            _cachedCredentialSecret = password;
            await PersistSessionAsync(CurrentSession, password, cancellationToken);
            return CurrentSession;
        }

        return await SignInWithTokenApiAsync(backendBaseUri, userName, password, cancellationToken);
    }

    public async Task SignOutAsync(CancellationToken cancellationToken)
    {
        BackendAuthSessionDto? session = CurrentSession;
        CurrentSession = null;
        _cachedCredentialSecret = null;

        if (session?.AuthMode == DesktopAuthModes.TokenApi)
        {
            await TryLogoutTokenSessionAsync(session, cancellationToken);
        }

        await _secureCredentialStorageService.RemoveSecretAsync(AccessTokenResource, SecretUser, cancellationToken);
        await _secureCredentialStorageService.RemoveSecretAsync(RefreshTokenResource, SecretUser, cancellationToken);
        await _secureCredentialStorageService.RemoveSecretAsync(BasicPasswordResource, SecretUser, cancellationToken);
        await _sessionStore.DeleteAsync(cancellationToken);
    }

    public void ApplyAuthentication(HttpRequestMessage message)
    {
        if (CurrentSession == null)
        {
            return;
        }

        if (CurrentSession.AuthMode == DesktopAuthModes.Basic)
        {
            BackendBasicAuthentication.Apply(
                message,
                CurrentSession.UserName,
                _cachedCredentialSecret,
                _connectionSettingsService.Current);
            return;
        }

        message.Headers.Authorization = new AuthenticationHeaderValue("Bearer", CurrentSession.AccessToken);
    }

    private async Task<BackendAuthSessionDto?> RestoreBasicSessionAsync(
        BackendStoredAuthMetadata metadata,
        CancellationToken cancellationToken)
    {
        string? password = await _secureCredentialStorageService.ReadSecretAsync(
            BasicPasswordResource,
            SecretUser,
            cancellationToken);
        if (string.IsNullOrWhiteSpace(password))
        {
            return null;
        }

        try
        {
            await _connectionSettingsService.EnsureBackendTransportAsync(cancellationToken);
            DesktopConnectionSettingsDto settings = _connectionSettingsService.Current;
            Uri targetBackendBaseUri = GetBackendBaseUri();
            await ValidateBasicAsync(targetBackendBaseUri, metadata.UserName, password, settings, cancellationToken);
            _cachedCredentialSecret = password;
            CurrentSession = BackendAuthSessionFactory.CreateBasicSession(targetBackendBaseUri, metadata.UserName, settings);
            await PersistSessionAsync(CurrentSession, password, cancellationToken);
            return CurrentSession;
        }
        catch
        {
            CurrentSession = null;
            _cachedCredentialSecret = null;
            return null;
        }
    }

    private async Task<BackendAuthSessionDto?> RestoreTokenSessionAsync(
        BackendStoredAuthMetadata metadata,
        CancellationToken cancellationToken)
    {
        Uri targetBackendBaseUri = GetBackendBaseUri();
        bool sameBackend = SameBackend(metadata.BackendBaseUrl, targetBackendBaseUri);
        string? accessToken = await _secureCredentialStorageService.ReadSecretAsync(
            AccessTokenResource,
            SecretUser,
            cancellationToken);
        string? refreshToken = await _secureCredentialStorageService.ReadSecretAsync(
            RefreshTokenResource,
            SecretUser,
            cancellationToken);
        if (string.IsNullOrWhiteSpace(refreshToken))
        {
            return null;
        }

        if (sameBackend
            && !string.IsNullOrWhiteSpace(accessToken)
            && metadata.AccessTokenExpiresAt > DateTimeOffset.UtcNow.AddMinutes(1))
        {
            CurrentSession = new BackendAuthSessionDto(
                targetBackendBaseUri.ToString().TrimEnd('/'),
                metadata.UserName,
                DesktopAuthModes.TokenApi,
                accessToken,
                refreshToken,
                metadata.AccessTokenExpiresAt,
                metadata.UserId,
                metadata.DisplayName,
                metadata.RequiresCodexLogin,
                metadata.CodexAuthMode,
                metadata.RemoteContinuationEnabled);
            return CurrentSession;
        }

        if (!sameBackend)
        {
            return null;
        }

        try
        {
            CurrentSession = await RefreshTokenSessionAsync(metadata, refreshToken, cancellationToken);
            return CurrentSession;
        }
        catch
        {
            CurrentSession = null;
            return null;
        }
    }

    private async Task<BackendAuthSessionDto> RefreshTokenSessionAsync(
        BackendStoredAuthMetadata metadata,
        string refreshToken,
        CancellationToken cancellationToken)
    {
        await _connectionSettingsService.EnsureBackendTransportAsync(cancellationToken);
        string deviceId = await _devicePresenceService.GetOrCreateDeviceIdAsync(cancellationToken);
        using var client = new HttpClient { BaseAddress = GetBackendBaseUri() };
        var request = new BackendRefreshRequestDto(refreshToken, deviceId, "AgentTaskManager.Desktop", "0.1.0");
        using HttpResponseMessage response = await client.PostAsJsonAsync(
            "api/auth/refresh",
            request,
            DesktopJson.Default,
            cancellationToken);
        if (!response.IsSuccessStatusCode)
        {
            await SignOutAsync(cancellationToken);
            throw new HttpRequestException($"Backend refresh failed with {(int)response.StatusCode}.");
        }

        BackendSignInResponseDto refreshed =
            (await response.Content.ReadFromJsonAsync<BackendSignInResponseDto>(DesktopJson.Default, cancellationToken))
            ?? throw new InvalidOperationException("Backend refresh response was empty.");
        var session = new BackendAuthSessionDto(
            GetBackendBaseUri().ToString().TrimEnd('/'),
            metadata.UserName,
            DesktopAuthModes.TokenApi,
            refreshed.AccessToken,
            refreshed.RefreshToken,
            refreshed.AccessTokenExpiresAt,
            refreshed.UserId,
            refreshed.DisplayName,
            refreshed.RequiresCodexLogin,
            refreshed.CodexAuthMode,
            refreshed.RemoteContinuationEnabled);
        _cachedCredentialSecret = null;
        await PersistSessionAsync(session, refreshed.RefreshToken, cancellationToken);
        return session;
    }

    private async Task<BackendAuthSessionDto> SignInWithTokenApiAsync(
        Uri backendBaseUri,
        string userName,
        string password,
        CancellationToken cancellationToken)
    {
        string deviceId = await _devicePresenceService.GetOrCreateDeviceIdAsync(cancellationToken);
        using var client = new HttpClient { BaseAddress = backendBaseUri };
        var request = new BackendSignInRequestDto(userName, password, deviceId, "AgentTaskManager.Desktop", "0.1.0");
        using HttpResponseMessage response = await client.PostAsJsonAsync(
            "api/auth/login",
            request,
            DesktopJson.Default,
            cancellationToken);
        response.EnsureSuccessStatusCode();

        BackendSignInResponseDto signInResponse =
            (await response.Content.ReadFromJsonAsync<BackendSignInResponseDto>(DesktopJson.Default, cancellationToken))
            ?? throw new InvalidOperationException("Backend sign-in response was empty.");
        CurrentSession = new BackendAuthSessionDto(
            backendBaseUri.ToString().TrimEnd('/'),
            userName,
            DesktopAuthModes.TokenApi,
            signInResponse.AccessToken,
            signInResponse.RefreshToken,
            signInResponse.AccessTokenExpiresAt,
            signInResponse.UserId,
            signInResponse.DisplayName,
            signInResponse.RequiresCodexLogin,
            signInResponse.CodexAuthMode,
            signInResponse.RemoteContinuationEnabled);
        _cachedCredentialSecret = null;
        await PersistSessionAsync(CurrentSession, signInResponse.RefreshToken, cancellationToken);
        return CurrentSession;
    }

    private static async Task ValidateBasicAsync(
        Uri backendBaseUri,
        string userName,
        string password,
        DesktopConnectionSettingsDto settings,
        CancellationToken cancellationToken)
    {
        using var client = new HttpClient { BaseAddress = backendBaseUri };
        using var request = new HttpRequestMessage(HttpMethod.Get, "api/runtime/status");
        BackendBasicAuthentication.Apply(request, userName, password, settings);
        using HttpResponseMessage response = await client.SendAsync(request, cancellationToken);
        if (response.IsSuccessStatusCode)
        {
            return;
        }

        if (response.StatusCode == System.Net.HttpStatusCode.NotFound)
        {
            throw new HttpRequestException(
                "The selected backend does not expose '/api/runtime/status'. Deploy the current AgentTaskManager web app on the remote host.");
        }

        string body = await response.Content.ReadAsStringAsync(cancellationToken);
        throw new HttpRequestException($"Backend basic auth failed with {(int)response.StatusCode}: {body}");
    }

    private async Task PersistSessionAsync(
        BackendAuthSessionDto session,
        string credentialSecret,
        CancellationToken cancellationToken)
    {
        if (session.AuthMode == DesktopAuthModes.Basic)
        {
            await _secureCredentialStorageService.RemoveSecretAsync(AccessTokenResource, SecretUser, cancellationToken);
            await _secureCredentialStorageService.RemoveSecretAsync(RefreshTokenResource, SecretUser, cancellationToken);
            await _secureCredentialStorageService.StoreSecretAsync(
                BasicPasswordResource,
                SecretUser,
                credentialSecret,
                cancellationToken);
        }
        else
        {
            await _secureCredentialStorageService.StoreSecretAsync(
                AccessTokenResource,
                SecretUser,
                session.AccessToken,
                cancellationToken);
            await _secureCredentialStorageService.StoreSecretAsync(
                RefreshTokenResource,
                SecretUser,
                session.RefreshToken,
                cancellationToken);
            await _secureCredentialStorageService.RemoveSecretAsync(BasicPasswordResource, SecretUser, cancellationToken);
        }

        await _sessionStore.SaveAsync(session, cancellationToken);
    }

    private async Task TryLogoutTokenSessionAsync(BackendAuthSessionDto session, CancellationToken cancellationToken)
    {
        try
        {
            using var client = new HttpClient { BaseAddress = GetBackendBaseUri() };
            using var request = new HttpRequestMessage(HttpMethod.Post, "api/auth/logout");
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", session.AccessToken);
            await client.SendAsync(request, cancellationToken);
        }
        catch
        {
        }
    }

    private static Uri EnsureBaseAddress(Uri backendBaseUri)
        => new($"{backendBaseUri.ToString().TrimEnd('/')}/");

    private static bool SameBackend(string persistedBackendBaseUrl, Uri currentBackendBaseUri)
        => string.Equals(
            persistedBackendBaseUrl.TrimEnd('/'),
            currentBackendBaseUri.ToString().TrimEnd('/'),
            StringComparison.OrdinalIgnoreCase);
}
