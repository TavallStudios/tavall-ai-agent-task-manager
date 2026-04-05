namespace AgentTaskManager.AutomationHost;

internal sealed record HostRuntimeOptions(
    string HttpPrefix,
    string? BearerToken,
    int LeaseTtlSeconds,
    string ServiceVersion)
{
    internal bool AuthRequired => !string.IsNullOrWhiteSpace(BearerToken);
}

