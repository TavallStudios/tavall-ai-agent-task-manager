using AgentTaskManager.Desktop.Contracts;
using System.Net.Http.Json;

namespace AgentTaskManager.Desktop.Services;

public sealed class RuntimeSessionClientService : IRuntimeSessionClientService
{
    private readonly IBackendAuthService _backendAuthService;
    private readonly IDesktopConnectionSettingsService _connectionSettingsService;
    private readonly IDevicePresenceService _devicePresenceService;

    public RuntimeSessionClientService(
        IBackendAuthService backendAuthService,
        IDesktopConnectionSettingsService connectionSettingsService,
        IDevicePresenceService devicePresenceService)
    {
        _backendAuthService = backendAuthService;
        _connectionSettingsService = connectionSettingsService;
        _devicePresenceService = devicePresenceService;
    }

    public Task MarkConnectedAsync(string sessionId, RuntimeConnectedRequestDto request, CancellationToken cancellationToken)
        => PostAsync(sessionId, "connected", request, cancellationToken);

    public Task MarkDisconnectedAsync(string sessionId, RuntimeDisconnectedRequestDto request, CancellationToken cancellationToken)
        => PostAsync(sessionId, "disconnected", request, cancellationToken);

    public Task PublishEventAsync(string sessionId, RuntimeEventPublishRequestDto request, CancellationToken cancellationToken)
        => PostAsync(sessionId, "events", request, cancellationToken);

    private async Task PostAsync(string sessionId, string suffix, object payload, CancellationToken cancellationToken)
    {
        await _connectionSettingsService.EnsureBackendTransportAsync(cancellationToken);
        using var client = new HttpClient
        {
            BaseAddress = _backendAuthService.GetBackendBaseUri()
        };
        using HttpRequestMessage message = await CreateRequestAsync(
            $"api/codex-client/sessions/{Uri.EscapeDataString(sessionId)}/runtime/{suffix}",
            payload,
            cancellationToken);
        using HttpResponseMessage response = await client.SendAsync(message, cancellationToken);
        if (response.IsSuccessStatusCode)
        {
            return;
        }

        string body = await response.Content.ReadAsStringAsync(cancellationToken);
        throw new HttpRequestException($"Runtime update '{suffix}' failed with {(int)response.StatusCode}: {body}");
    }

    private async Task<HttpRequestMessage> CreateRequestAsync(string path, object payload, CancellationToken cancellationToken)
    {
        string deviceId = await _devicePresenceService.GetOrCreateDeviceIdAsync(cancellationToken);
        string deviceName = await _devicePresenceService.GetDeviceNameAsync(cancellationToken);
        var message = new HttpRequestMessage(HttpMethod.Post, path);
        _backendAuthService.ApplyAuthentication(message);
        message.Headers.Add("X-AgentTaskManager-Device", deviceId);
        message.Headers.Add("X-AgentTaskManager-Host", deviceName);
        message.Content = JsonContent.Create(payload, options: DesktopJson.Default);
        return message;
    }
}
