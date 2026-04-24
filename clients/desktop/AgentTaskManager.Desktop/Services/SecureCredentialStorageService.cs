using Windows.Security.Credentials;

namespace AgentTaskManager.Desktop.Services;

public sealed class SecureCredentialStorageService : ISecureCredentialStorageService
{
    private readonly PasswordVault _passwordVault = new();

    public async Task StoreSecretAsync(
        string resource,
        string userName,
        string secret,
        CancellationToken cancellationToken)
    {
        await Task.CompletedTask;
        await RemoveSecretAsync(resource, userName, cancellationToken);
        _passwordVault.Add(new PasswordCredential(resource, userName, secret));
    }

    public async Task<string?> ReadSecretAsync(
        string resource,
        string userName,
        CancellationToken cancellationToken)
    {
        await Task.CompletedTask;
        try
        {
            PasswordCredential credential = _passwordVault.Retrieve(resource, userName);
            credential.RetrievePassword();
            return credential.Password;
        }
        catch
        {
            return null;
        }
    }

    public Task RemoveSecretAsync(string resource, string userName, CancellationToken cancellationToken)
    {
        try
        {
            PasswordCredential credential = _passwordVault.Retrieve(resource, userName);
            _passwordVault.Remove(credential);
        }
        catch
        {
            // The credential is already absent.
        }
        return Task.CompletedTask;
    }
}
