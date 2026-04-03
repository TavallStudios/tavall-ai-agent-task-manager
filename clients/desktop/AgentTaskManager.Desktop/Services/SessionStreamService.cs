using AgentTaskManager.Desktop.Contracts;
using System.Runtime.CompilerServices;
using System.Text;
using System.Text.Json;

namespace AgentTaskManager.Desktop.Services;

public sealed class SessionStreamService : ISessionStreamService
{
    private readonly IBackendAuthService _backendAuthService;
    private readonly IDesktopConnectionSettingsService _connectionSettingsService;
    private readonly IDevicePresenceService _devicePresenceService;

    public SessionStreamService(
        IBackendAuthService backendAuthService,
        IDesktopConnectionSettingsService connectionSettingsService,
        IDevicePresenceService devicePresenceService)
    {
        _backendAuthService = backendAuthService;
        _connectionSettingsService = connectionSettingsService;
        _devicePresenceService = devicePresenceService;
    }

    public async IAsyncEnumerable<SessionEventEnvelopeDto> StreamEventsAsync(
        string sessionId,
        string? afterEventId,
        int replayLimit,
        [EnumeratorCancellation] CancellationToken cancellationToken)
    {
        await _connectionSettingsService.EnsureBackendTransportAsync(cancellationToken);
        string path = $"api/codex-client/sessions/{Uri.EscapeDataString(sessionId)}/events/stream?replayLimit={replayLimit}";
        if (!string.IsNullOrWhiteSpace(afterEventId))
        {
            path += $"&afterEventId={Uri.EscapeDataString(afterEventId)}";
        }

        using var client = new HttpClient
        {
            BaseAddress = _backendAuthService.GetBackendBaseUri(),
            Timeout = Timeout.InfiniteTimeSpan
        };
        using HttpRequestMessage message = await CreateRequestAsync(path, cancellationToken);
        using HttpResponseMessage response = await client.SendAsync(
            message,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken);
        response.EnsureSuccessStatusCode();

        await using Stream stream = await response.Content.ReadAsStreamAsync(cancellationToken);
        using var reader = new StreamReader(stream);
        var dataBuilder = new StringBuilder();

        while (!reader.EndOfStream && !cancellationToken.IsCancellationRequested)
        {
            string? line = await reader.ReadLineAsync();
            if (line == null)
            {
                break;
            }

            if (line.Length == 0)
            {
                if (dataBuilder.Length > 0)
                {
                    string payload = dataBuilder.ToString();
                    dataBuilder.Clear();
                    SessionEventEnvelopeDto? item = JsonSerializer.Deserialize<SessionEventEnvelopeDto>(payload, DesktopJson.Default);
                    if (item != null)
                    {
                        yield return item;
                    }
                }

                continue;
            }

            if (line.StartsWith("data:", StringComparison.Ordinal))
            {
                dataBuilder.AppendLine(line[5..].TrimStart());
            }
        }
    }

    private async Task<HttpRequestMessage> CreateRequestAsync(string path, CancellationToken cancellationToken)
    {
        string deviceId = await _devicePresenceService.GetOrCreateDeviceIdAsync(cancellationToken);
        string deviceName = await _devicePresenceService.GetDeviceNameAsync(cancellationToken);
        var message = new HttpRequestMessage(HttpMethod.Get, path);
        _backendAuthService.ApplyAuthentication(message);
        message.Headers.Add("X-AgentTaskManager-Device", deviceId);
        message.Headers.Add("X-AgentTaskManager-Host", deviceName);
        message.Headers.Accept.ParseAdd("text/event-stream");
        return message;
    }
}
