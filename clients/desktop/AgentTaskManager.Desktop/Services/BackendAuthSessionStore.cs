using AgentTaskManager.Desktop.Contracts;
using System.Text.Json;

namespace AgentTaskManager.Desktop.Services;

public sealed class BackendAuthSessionStore
{
    internal async Task<BackendStoredAuthMetadata?> LoadAsync(CancellationToken cancellationToken)
    {
        DesktopStoragePaths.EnsureCreated();
        if (!File.Exists(DesktopStoragePaths.AuthSessionFile))
        {
            return null;
        }

        string json = await File.ReadAllTextAsync(DesktopStoragePaths.AuthSessionFile, cancellationToken);
        return JsonSerializer.Deserialize<BackendStoredAuthMetadata>(json, DesktopJson.Default);
    }

    internal async Task SaveAsync(BackendAuthSessionDto session, CancellationToken cancellationToken)
    {
        DesktopStoragePaths.EnsureCreated();
        var metadata = new BackendStoredAuthMetadata(
            session.UserName,
            session.AuthMode,
            session.BackendBaseUrl,
            session.AccessTokenExpiresAt,
            session.UserId,
            session.DisplayName,
            session.RequiresCodexLogin,
            session.CodexAuthMode,
            session.RemoteContinuationEnabled);
        string json = JsonSerializer.Serialize(metadata, DesktopJson.Default);
        await File.WriteAllTextAsync(DesktopStoragePaths.AuthSessionFile, json, cancellationToken);
    }

    internal Task DeleteAsync(CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        if (File.Exists(DesktopStoragePaths.AuthSessionFile))
        {
            File.Delete(DesktopStoragePaths.AuthSessionFile);
        }

        return Task.CompletedTask;
    }
}

internal sealed record BackendStoredAuthMetadata(
    string UserName,
    string AuthMode,
    string BackendBaseUrl,
    DateTimeOffset AccessTokenExpiresAt,
    string UserId,
    string DisplayName,
    bool RequiresCodexLogin,
    string CodexAuthMode,
    bool RemoteContinuationEnabled)
{
    public BackendAuthSessionDto ToSession(string accessToken = "", string refreshToken = "")
        => new(
            BackendBaseUrl,
            UserName,
            AuthMode,
            accessToken,
            refreshToken,
            AccessTokenExpiresAt,
            UserId,
            DisplayName,
            RequiresCodexLogin,
            CodexAuthMode,
            RemoteContinuationEnabled);
}
