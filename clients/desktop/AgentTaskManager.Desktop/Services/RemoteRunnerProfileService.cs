using AgentTaskManager.Desktop.Contracts;
using System.Net.Http.Json;
using System.Text.Json;

namespace AgentTaskManager.Desktop.Services;

public sealed class RemoteRunnerProfileService : IRemoteRunnerProfileService
{
    private const string RunnerSecretResource = "remote-runner-auth-token";

    private readonly IBackendAuthService _backendAuthService;
    private readonly IDesktopConnectionSettingsService _connectionSettingsService;
    private readonly ISecureCredentialStorageService _credentialStorageService;

    public RemoteRunnerProfileService(
        IBackendAuthService backendAuthService,
        IDesktopConnectionSettingsService connectionSettingsService,
        ISecureCredentialStorageService credentialStorageService)
    {
        _backendAuthService = backendAuthService;
        _connectionSettingsService = connectionSettingsService;
        _credentialStorageService = credentialStorageService;
    }

    public async Task<IReadOnlyList<RemoteRunnerProfileDto>> ListProfilesAsync(CancellationToken cancellationToken)
    {
        IReadOnlyList<RemoteRunnerProfileDto>? backend = await TryLoadBackendAsync(cancellationToken);
        if (backend is not null)
        {
            await SaveFileAsync(backend, cancellationToken);
            return backend;
        }

        return await LoadFileAsync(cancellationToken);
    }

    public async Task<RemoteRunnerProfileDto> SaveProfileAsync(
        RemoteRunnerProfileDto profile,
        string? runnerAuthToken,
        CancellationToken cancellationToken)
    {
        RemoteRunnerProfileDto normalized = Normalize(profile);
        List<RemoteRunnerProfileDto> profiles = (await LoadFileAsync(cancellationToken)).ToList();
        profiles.RemoveAll(item => string.Equals(item.ProfileId, normalized.ProfileId, StringComparison.OrdinalIgnoreCase));
        profiles.Add(normalized);
        await SaveFileAsync(profiles, cancellationToken);

        string secretKey = BuildSecretKey(normalized);
        if (!string.IsNullOrWhiteSpace(runnerAuthToken))
        {
            await _credentialStorageService.StoreSecretAsync(
                RunnerSecretResource,
                secretKey,
                runnerAuthToken,
                cancellationToken);
        }

        await TrySaveBackendAsync(normalized, cancellationToken);
        return normalized;
    }

    public async Task DeleteProfileAsync(string profileId, CancellationToken cancellationToken)
    {
        List<RemoteRunnerProfileDto> profiles = (await LoadFileAsync(cancellationToken)).ToList();
        RemoteRunnerProfileDto? existing = profiles.FirstOrDefault(item =>
            string.Equals(item.ProfileId, profileId, StringComparison.OrdinalIgnoreCase));
        if (existing is null)
        {
            return;
        }

        profiles.Remove(existing);
        if (profiles.All(item => !item.Selected) && profiles.Count > 0)
        {
            profiles[0] = profiles[0] with { Selected = true };
        }

        await SaveFileAsync(profiles, cancellationToken);
        await _credentialStorageService.RemoveSecretAsync(RunnerSecretResource, BuildSecretKey(existing), cancellationToken);
        await TryDeleteBackendAsync(existing.ProfileId, cancellationToken);
    }

    public async Task SelectProfileAsync(string profileId, CancellationToken cancellationToken)
    {
        List<RemoteRunnerProfileDto> profiles = (await LoadFileAsync(cancellationToken)).ToList();
        if (profiles.Count == 0)
        {
            return;
        }

        for (int index = 0; index < profiles.Count; index++)
        {
            profiles[index] = profiles[index] with { Selected = string.Equals(profiles[index].ProfileId, profileId, StringComparison.OrdinalIgnoreCase) };
        }

        await SaveFileAsync(profiles, cancellationToken);
        await TrySelectBackendAsync(profileId, cancellationToken);
    }

    public async Task<RemoteRunnerConnectionTestDto> TestProfileAsync(RemoteRunnerProfileDto profile, CancellationToken cancellationToken)
    {
        RemoteRunnerProfileDto normalized = Normalize(profile);
        string token = await _credentialStorageService.ReadSecretAsync(
                           RunnerSecretResource,
                           BuildSecretKey(normalized),
                           cancellationToken)
                       ?? string.Empty;
        string baseUrl = normalized.BaseUrl.TrimEnd('/');
        using var client = new HttpClient { Timeout = TimeSpan.FromSeconds(10) };
        string healthStatus = "unreachable";
        string capabilitiesSummary = "capabilities unavailable";
        string effectiveCommandPath = "/api/automation/command";
        try
        {
            using HttpRequestMessage healthRequest = new(HttpMethod.Get, $"{baseUrl}/api/automation/health");
            ApplyRunnerAuth(healthRequest, token);
            using HttpResponseMessage healthResponse = await client.SendAsync(healthRequest, cancellationToken);
            healthStatus = healthResponse.IsSuccessStatusCode ? "ok" : $"http-{(int)healthResponse.StatusCode}";
        }
        catch (Exception exception)
        {
            return new RemoteRunnerConnectionTestDto(false, exception.Message, healthStatus, capabilitiesSummary, effectiveCommandPath);
        }

        try
        {
            using HttpRequestMessage capabilitiesRequest = new(HttpMethod.Get, $"{baseUrl}/api/automation/capabilities");
            ApplyRunnerAuth(capabilitiesRequest, token);
            using HttpResponseMessage capabilitiesResponse = await client.SendAsync(capabilitiesRequest, cancellationToken);
            if (capabilitiesResponse.IsSuccessStatusCode)
            {
                JsonElement payload = await capabilitiesResponse.Content.ReadFromJsonAsync<JsonElement>(DesktopJson.Default, cancellationToken);
                capabilitiesSummary = "available";
                effectiveCommandPath = ResolveCommandPath(payload);
            }
            else
            {
                capabilitiesSummary = $"http-{(int)capabilitiesResponse.StatusCode}";
            }
        }
        catch
        {
            // Keep fallback summary.
        }

        bool success = string.Equals(healthStatus, "ok", StringComparison.OrdinalIgnoreCase);
        return new RemoteRunnerConnectionTestDto(
            success,
            success ? "Runner profile test succeeded." : "Runner profile test failed.",
            healthStatus,
            capabilitiesSummary,
            effectiveCommandPath);
    }

    private async Task<IReadOnlyList<RemoteRunnerProfileDto>?> TryLoadBackendAsync(CancellationToken cancellationToken)
    {
        try
        {
            await _connectionSettingsService.EnsureBackendTransportAsync(cancellationToken);
            using var client = new HttpClient { BaseAddress = _backendAuthService.GetBackendBaseUri() };
            using var request = new HttpRequestMessage(HttpMethod.Get, "api/desktop/remote-runners");
            _backendAuthService.ApplyAuthentication(request);
            using HttpResponseMessage response = await client.SendAsync(request, cancellationToken);
            if (!response.IsSuccessStatusCode)
            {
                return null;
            }

            IReadOnlyList<RemoteRunnerProfileDto>? value = await response.Content.ReadFromJsonAsync<IReadOnlyList<RemoteRunnerProfileDto>>(DesktopJson.Default, cancellationToken);
            return value?.Select(Normalize).ToArray();
        }
        catch
        {
            return null;
        }
    }

    private async Task TrySaveBackendAsync(RemoteRunnerProfileDto profile, CancellationToken cancellationToken)
    {
        try
        {
            await _connectionSettingsService.EnsureBackendTransportAsync(cancellationToken);
            using var client = new HttpClient { BaseAddress = _backendAuthService.GetBackendBaseUri() };
            using var request = new HttpRequestMessage(HttpMethod.Put, $"api/desktop/remote-runners/{Uri.EscapeDataString(profile.ProfileId)}")
            {
                Content = JsonContent.Create(profile, options: DesktopJson.Default)
            };
            _backendAuthService.ApplyAuthentication(request);
            _ = await client.SendAsync(request, cancellationToken);
        }
        catch
        {
            // Local file persistence is the fallback source of truth.
        }
    }

    private async Task TryDeleteBackendAsync(string profileId, CancellationToken cancellationToken)
    {
        try
        {
            await _connectionSettingsService.EnsureBackendTransportAsync(cancellationToken);
            using var client = new HttpClient { BaseAddress = _backendAuthService.GetBackendBaseUri() };
            using var request = new HttpRequestMessage(HttpMethod.Delete, $"api/desktop/remote-runners/{Uri.EscapeDataString(profileId)}");
            _backendAuthService.ApplyAuthentication(request);
            _ = await client.SendAsync(request, cancellationToken);
        }
        catch
        {
        }
    }

    private async Task TrySelectBackendAsync(string profileId, CancellationToken cancellationToken)
    {
        try
        {
            await _connectionSettingsService.EnsureBackendTransportAsync(cancellationToken);
            using var client = new HttpClient { BaseAddress = _backendAuthService.GetBackendBaseUri() };
            using var request = new HttpRequestMessage(HttpMethod.Post, $"api/desktop/remote-runners/{Uri.EscapeDataString(profileId)}/select");
            _backendAuthService.ApplyAuthentication(request);
            _ = await client.SendAsync(request, cancellationToken);
        }
        catch
        {
        }
    }

    private static async Task<IReadOnlyList<RemoteRunnerProfileDto>> LoadFileAsync(CancellationToken cancellationToken)
    {
        DesktopStoragePaths.EnsureCreated();
        if (!File.Exists(DesktopStoragePaths.RemoteRunnerProfilesFile))
        {
            return Array.Empty<RemoteRunnerProfileDto>();
        }

        string json = await File.ReadAllTextAsync(DesktopStoragePaths.RemoteRunnerProfilesFile, cancellationToken);
        IReadOnlyList<RemoteRunnerProfileDto>? items = JsonSerializer.Deserialize<IReadOnlyList<RemoteRunnerProfileDto>>(json, DesktopJson.Default);
        return (items ?? []).Select(Normalize).ToArray();
    }

    private static async Task SaveFileAsync(IReadOnlyList<RemoteRunnerProfileDto> profiles, CancellationToken cancellationToken)
    {
        DesktopStoragePaths.EnsureCreated();
        string json = JsonSerializer.Serialize(profiles.Select(Normalize).ToArray(), DesktopJson.Default);
        await File.WriteAllTextAsync(DesktopStoragePaths.RemoteRunnerProfilesFile, json, cancellationToken);
    }

    private static RemoteRunnerProfileDto Normalize(RemoteRunnerProfileDto profile)
    {
        string normalizedProfileId = string.IsNullOrWhiteSpace(profile.ProfileId)
            ? Guid.NewGuid().ToString("N")
            : profile.ProfileId.Trim();
        return profile with
        {
            ProfileId = normalizedProfileId,
            DisplayName = string.IsNullOrWhiteSpace(profile.DisplayName) ? "Remote Runner" : profile.DisplayName.Trim(),
            BaseUrl = NormalizeBaseUrl(profile.BaseUrl),
            TransportMode = string.IsNullOrWhiteSpace(profile.TransportMode) ? "DIRECT_HTTP" : profile.TransportMode.Trim().ToUpperInvariant(),
            SshHost = profile.SshHost?.Trim() ?? string.Empty,
            SshPort = profile.SshPort <= 0 ? 22 : profile.SshPort,
            SshUser = string.IsNullOrWhiteSpace(profile.SshUser) ? "ubuntu" : profile.SshUser.Trim(),
            RunnerAuthTokenReference = string.IsNullOrWhiteSpace(profile.RunnerAuthTokenReference)
                ? $"runner-token-{normalizedProfileId}"
                : profile.RunnerAuthTokenReference.Trim(),
            DefaultScenarioId = string.IsNullOrWhiteSpace(profile.DefaultScenarioId)
                ? "hytale/launch-and-join-smoke"
                : profile.DefaultScenarioId.Trim(),
            TerminalCommand = profile.TerminalCommand?.Trim() ?? string.Empty,
            UpdatedAt = profile.UpdatedAt == default ? DateTimeOffset.UtcNow : profile.UpdatedAt
        };
    }

    private static string NormalizeBaseUrl(string value)
        => string.IsNullOrWhiteSpace(value)
            ? "http://127.0.0.1:54123"
            : value.Trim().TrimEnd('/');

    private static string BuildSecretKey(RemoteRunnerProfileDto profile)
        => string.IsNullOrWhiteSpace(profile.RunnerAuthTokenReference)
            ? $"runner-token-{profile.ProfileId}"
            : profile.RunnerAuthTokenReference;

    private static void ApplyRunnerAuth(HttpRequestMessage request, string token)
    {
        if (!string.IsNullOrWhiteSpace(token))
        {
            request.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", token);
        }
    }

    private static string ResolveCommandPath(JsonElement payload)
    {
        if (payload.ValueKind != JsonValueKind.Object
            || !payload.TryGetProperty("result", out JsonElement result)
            || result.ValueKind != JsonValueKind.Object
            || !result.TryGetProperty("endpoints", out JsonElement endpoints)
            || endpoints.ValueKind != JsonValueKind.Object
            || !endpoints.TryGetProperty("command", out JsonElement command)
            || command.ValueKind != JsonValueKind.String)
        {
            return "/api/automation/command";
        }

        return command.GetString() ?? "/api/automation/command";
    }
}
