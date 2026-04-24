using AgentTaskManager.Desktop.Contracts;
using CommunityToolkit.Mvvm.ComponentModel;

namespace AgentTaskManager.Desktop.ViewModels;

public sealed class StatusStripViewModel : ObservableObject
{
    private string _sessionStatus = "No active session";
    private string _runtimeStatus = "Runtime disconnected";
    private string _leaseStatus = "No runtime lease";
    private string _streamStatus = "Live event stream idle";
    private string _deviceStatus = "No device presence";

    public string SessionStatus
    {
        get => _sessionStatus;
        set => SetProperty(ref _sessionStatus, value);
    }

    public string RuntimeStatus
    {
        get => _runtimeStatus;
        set => SetProperty(ref _runtimeStatus, value);
    }

    public string LeaseStatus
    {
        get => _leaseStatus;
        set => SetProperty(ref _leaseStatus, value);
    }

    public string StreamStatus
    {
        get => _streamStatus;
        set => SetProperty(ref _streamStatus, value);
    }

    public string DeviceStatus
    {
        get => _deviceStatus;
        set => SetProperty(ref _deviceStatus, value);
    }

    public void ApplySignedOut()
    {
        SessionStatus = "No active session";
        RuntimeStatus = "Runtime disconnected";
        LeaseStatus = "No runtime lease";
        StreamStatus = "Live event stream idle";
        DeviceStatus = "Sign in to load device presence.";
    }

    public void ApplyConnectedBackend(string transportStatus)
    {
        SessionStatus = "No active session";
        RuntimeStatus = "Runtime disconnected";
        LeaseStatus = "No runtime lease";
        StreamStatus = transportStatus;
        DeviceStatus = "Signed in. Create or load a session.";
    }

    public void ApplySession(SessionDetailDto detail)
    {
        SessionStatus = $"{detail.Summary.Title} [{detail.Summary.LifecycleState}]";
        RuntimeStatus = $"Runtime {detail.RuntimeConnection.ConnectionState} ({detail.RuntimeConnection.AuthMode})";
        LeaseStatus = $"Lease {detail.RuntimeLease.LeaseState} on {detail.RuntimeLease.OwnerHostName}";
        DeviceStatus = $"{detail.Devices.Count} attached device(s). Runtime owner {detail.RuntimeLease.OwnerDeviceId}.";
    }

    public void SetStreamStatus(string message)
    {
        StreamStatus = message;
    }
}
