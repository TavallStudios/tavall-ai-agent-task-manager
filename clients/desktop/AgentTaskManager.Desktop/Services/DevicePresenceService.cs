namespace AgentTaskManager.Desktop.Services;

public sealed class DevicePresenceService : IDevicePresenceService
{
    private const string DeviceResource = "desktop-device-id";
    private const string DeviceUser = "current-user";

    private readonly ISecureCredentialStorageService _secureCredentialStorageService;

    public DevicePresenceService(ISecureCredentialStorageService secureCredentialStorageService)
    {
        _secureCredentialStorageService = secureCredentialStorageService;
    }

    public async Task<string> GetOrCreateDeviceIdAsync(CancellationToken cancellationToken)
    {
        string? existing = await _secureCredentialStorageService.ReadSecretAsync(DeviceResource, DeviceUser, cancellationToken);
        if (!string.IsNullOrWhiteSpace(existing))
        {
            return existing;
        }

        string deviceId = $"desktop-{Guid.NewGuid():N}"[..20];
        await _secureCredentialStorageService.StoreSecretAsync(DeviceResource, DeviceUser, deviceId, cancellationToken);
        return deviceId;
    }

    public Task<string> GetDeviceNameAsync(CancellationToken cancellationToken)
        => Task.FromResult(Environment.MachineName);
}
