using AgentTaskManager.Desktop.Contracts;
using System.Net.Http.Json;
using System.Text.Json;

namespace AgentTaskManager.Desktop.Services;

public sealed class SessionClientService : ISessionClientService
{
    private readonly IBackendAuthService _backendAuthService;
    private readonly IDevicePresenceService _devicePresenceService;
    private readonly IDesktopConnectionSettingsService _connectionSettingsService;

    public SessionClientService(
        IBackendAuthService backendAuthService,
        IDevicePresenceService devicePresenceService,
        IDesktopConnectionSettingsService connectionSettingsService)
    {
        _backendAuthService = backendAuthService;
        _devicePresenceService = devicePresenceService;
        _connectionSettingsService = connectionSettingsService;
    }

    public async Task<IReadOnlyList<SessionSummaryDto>> ListSessionsAsync(CancellationToken cancellationToken)
    {
        int limit = _connectionSettingsService.GetSessionListLimit();
        SessionListResponseDto response = await SendAsync<SessionListResponseDto>(
            HttpMethod.Get,
            $"api/codex-client/sessions?limit={limit}",
            content: null,
            cancellationToken);
        return response.Items;
    }

    public Task<SessionDetailDto> GetSessionAsync(string sessionId, CancellationToken cancellationToken)
        => SendAsync<SessionDetailDto>(
            HttpMethod.Get,
            $"api/codex-client/sessions/{Uri.EscapeDataString(sessionId)}",
            content: null,
            cancellationToken);

    public Task<SessionDetailDto> CreateSessionAsync(CreateSessionRequestDto request, CancellationToken cancellationToken)
        => SendAsync<SessionDetailDto>(
            HttpMethod.Post,
            "api/codex-client/sessions",
            request,
            cancellationToken);

    public Task<SessionDetailDto> AttachSessionAsync(string sessionId, AttachSessionRequestDto request, CancellationToken cancellationToken)
        => SendAsync<SessionDetailDto>(
            HttpMethod.Post,
            $"api/codex-client/sessions/{Uri.EscapeDataString(sessionId)}/attach",
            request,
            cancellationToken);

    public Task<SessionDetailDto> ResumeSessionAsync(string sessionId, ResumeSessionRequestDto request, CancellationToken cancellationToken)
        => SendAsync<SessionDetailDto>(
            HttpMethod.Post,
            $"api/codex-client/sessions/{Uri.EscapeDataString(sessionId)}/resume",
            request,
            cancellationToken);

    public Task<SessionDetailDto> SubmitTurnAsync(string sessionId, SubmitTurnRequestDto request, CancellationToken cancellationToken)
        => SendAsync<SessionDetailDto>(
            HttpMethod.Post,
            $"api/codex-client/sessions/{Uri.EscapeDataString(sessionId)}/turns",
            request,
            cancellationToken);

    public async Task<IReadOnlyList<SessionEventEnvelopeDto>> ListEventsAsync(
        string sessionId,
        string? afterEventId,
        int limit,
        CancellationToken cancellationToken)
    {
        string path = $"api/codex-client/sessions/{Uri.EscapeDataString(sessionId)}/events?limit={limit}";
        if (!string.IsNullOrWhiteSpace(afterEventId))
        {
            path += $"&afterEventId={Uri.EscapeDataString(afterEventId)}";
        }

        EventListResponseDto response = await SendAsync<EventListResponseDto>(
            HttpMethod.Get,
            path,
            content: null,
            cancellationToken);
        return response.Items;
    }

    private async Task<TResponse> SendAsync<TResponse>(
        HttpMethod method,
        string path,
        object? content,
        CancellationToken cancellationToken)
    {
        await _connectionSettingsService.EnsureBackendTransportAsync(cancellationToken);
        using var client = new HttpClient
        {
            BaseAddress = _backendAuthService.GetBackendBaseUri()
        };
        using HttpRequestMessage message = await CreateRequestAsync(method, path, content, cancellationToken);
        using HttpResponseMessage response = await client.SendAsync(message, cancellationToken);
        string payload = await response.Content.ReadAsStringAsync(cancellationToken);
        if (!response.IsSuccessStatusCode)
        {
            if (response.StatusCode == System.Net.HttpStatusCode.NotFound
                && path.StartsWith("api/codex-client/sessions", StringComparison.Ordinal))
            {
                throw new HttpRequestException(
                    "The backend does not expose the session platform APIs. Redeploy the current AgentTaskManager app and enable AGENT_TASK_MANAGER_CODEX_CLIENT_PLATFORM_ENABLED=true.");
            }

            throw new HttpRequestException($"Backend request to '{path}' failed with {(int)response.StatusCode}: {payload}");
        }

        TResponse? value = JsonSerializer.Deserialize<TResponse>(payload, DesktopJson.Default);
        return value ?? throw new InvalidOperationException($"Backend response for '{path}' was empty.");
    }

    private async Task<HttpRequestMessage> CreateRequestAsync(
        HttpMethod method,
        string path,
        object? content,
        CancellationToken cancellationToken)
    {
        string deviceId = await _devicePresenceService.GetOrCreateDeviceIdAsync(cancellationToken);
        string deviceName = await _devicePresenceService.GetDeviceNameAsync(cancellationToken);
        var message = new HttpRequestMessage(method, path);
        _backendAuthService.ApplyAuthentication(message);
        message.Headers.Add("X-AgentTaskManager-Device", deviceId);
        message.Headers.Add("X-AgentTaskManager-Host", deviceName);
        if (content != null)
        {
            message.Content = JsonContent.Create(content, options: DesktopJson.Default);
        }

        return message;
    }
}
