using AgentTaskManager.Desktop.Contracts;

namespace AgentTaskManager.Desktop.Services;

public sealed class RemoteSessionResumeService : IRemoteSessionResumeService
{
    private readonly ISessionClientService _sessionClientService;
    private readonly IDevicePresenceService _devicePresenceService;
    private readonly IDesktopConnectionSettingsService _connectionSettingsService;

    public RemoteSessionResumeService(
        ISessionClientService sessionClientService,
        IDevicePresenceService devicePresenceService,
        IDesktopConnectionSettingsService connectionSettingsService)
    {
        _sessionClientService = sessionClientService;
        _devicePresenceService = devicePresenceService;
        _connectionSettingsService = connectionSettingsService;
    }

    public async Task<SessionDetailDto> ResumeAsync(string sessionId, CancellationToken cancellationToken)
    {
        string deviceId = await _devicePresenceService.GetOrCreateDeviceIdAsync(cancellationToken);
        string deviceName = await _devicePresenceService.GetDeviceNameAsync(cancellationToken);
        var request = new ResumeSessionRequestDto(
            deviceId,
            deviceName,
            _connectionSettingsService.ManageLocalRuntime(),
            _connectionSettingsService.AllowRuntimeHandoff());
        return await _sessionClientService.ResumeSessionAsync(sessionId, request, cancellationToken);
    }
}
