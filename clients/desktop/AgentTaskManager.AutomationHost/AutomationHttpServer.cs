using System.Net;
using System.IO;
using System.Text;
using System.Text.Json;

namespace AgentTaskManager.AutomationHost;

internal sealed class AutomationHttpServer
{
    private readonly AutomationCommandRouter _router;
    private readonly JsonSerializerOptions _jsonOptions;

    internal AutomationHttpServer(AutomationCommandRouter router, JsonSerializerOptions jsonOptions)
    {
        _router = router;
        _jsonOptions = jsonOptions;
    }

    internal async Task<int> RunAsync(string prefix, CancellationToken cancellationToken)
    {
        using var listener = new HttpListener();
        listener.Prefixes.Add(NormalizePrefix(prefix));
        listener.Start();

        using CancellationTokenRegistration registration = cancellationToken.Register(() =>
        {
            try
            {
                listener.Stop();
            }
            catch
            {
            }
        });

        while (!cancellationToken.IsCancellationRequested)
        {
            HttpListenerContext context;
            try
            {
                context = await listener.GetContextAsync();
            }
            catch when (cancellationToken.IsCancellationRequested)
            {
                break;
            }
            catch (HttpListenerException)
            {
                break;
            }
            catch (ObjectDisposedException)
            {
                break;
            }

            await HandleContextAsync(context, cancellationToken);
        }

        return 0;
    }

    private async Task HandleContextAsync(HttpListenerContext context, CancellationToken cancellationToken)
    {
        string path = context.Request.Url?.AbsolutePath?.TrimEnd('/') ?? string.Empty;
        if (string.Equals(path, string.Empty, StringComparison.Ordinal))
        {
            path = "/";
        }

        if (string.Equals(path, "/health", StringComparison.OrdinalIgnoreCase) && string.Equals(context.Request.HttpMethod, "GET", StringComparison.OrdinalIgnoreCase))
        {
            await WriteJsonAsync(context.Response, 200, new { ok = true }, cancellationToken);
            return;
        }

        if (!string.Equals(path, "/request", StringComparison.OrdinalIgnoreCase) &&
            !string.Equals(path, "/execute", StringComparison.OrdinalIgnoreCase))
        {
            await WriteJsonAsync(context.Response, 404, new { ok = false, error = "not_found" }, cancellationToken);
            return;
        }

        if (!string.Equals(context.Request.HttpMethod, "POST", StringComparison.OrdinalIgnoreCase))
        {
            await WriteJsonAsync(context.Response, 405, new { ok = false, error = "method_not_allowed" }, cancellationToken);
            return;
        }

        string payload;
        using (var reader = new StreamReader(context.Request.InputStream, context.Request.ContentEncoding ?? Encoding.UTF8))
        {
            payload = await reader.ReadToEndAsync();
        }

        AutomationResponse response = await _router.ExecuteJsonAsync(payload, cancellationToken);
        int statusCode = response.Ok ? 200 : response.Error?.Code == "invalid_request" ? 400 : 500;
        await WriteJsonAsync(context.Response, statusCode, response, cancellationToken);
    }

    private async Task WriteJsonAsync(HttpListenerResponse response, int statusCode, object payload, CancellationToken cancellationToken)
    {
        response.StatusCode = statusCode;
        response.ContentType = "application/json; charset=utf-8";
        byte[] bytes = JsonSerializer.SerializeToUtf8Bytes(payload, payload.GetType(), _jsonOptions);
        response.ContentLength64 = bytes.Length;
        await response.OutputStream.WriteAsync(bytes, cancellationToken);
        response.OutputStream.Close();
    }

    private static string NormalizePrefix(string prefix)
        => prefix.EndsWith('/') ? prefix : prefix + "/";
}
