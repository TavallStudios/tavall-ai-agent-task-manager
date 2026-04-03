using AgentTaskManager.Desktop.Contracts;
using System.Net.Http.Headers;
using System.Text;

namespace AgentTaskManager.Desktop.Services;

internal static class BackendBasicAuthentication
{
    public static void Apply(
        HttpRequestMessage message,
        string userName,
        string? password,
        DesktopConnectionSettingsDto settings)
    {
        if (!string.IsNullOrWhiteSpace(password))
        {
            string pair = $"{userName}:{password}";
            string basic = Convert.ToBase64String(Encoding.ASCII.GetBytes(pair));
            message.Headers.Authorization = new AuthenticationHeaderValue("Basic", basic);
        }

        if (!settings.SendForwardedUserHeader)
        {
            return;
        }

        message.Headers.Remove(settings.ForwardedUserHeaderName);
        message.Headers.Add(settings.ForwardedUserHeaderName, userName);
    }
}
