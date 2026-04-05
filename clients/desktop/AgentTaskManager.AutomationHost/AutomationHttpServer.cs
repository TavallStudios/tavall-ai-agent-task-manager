using System.Net;
using System.IO;
using System.Text;
using System.Text.Json;

namespace AgentTaskManager.AutomationHost;

internal sealed class AutomationHttpServer
{
    private const string CanonicalHealthPath = "/api/automation/health";
    private const string CanonicalCapabilitiesPath = "/api/automation/capabilities";
    private const string CanonicalCommandPath = "/api/automation/command";
    private const string CanonicalLeaseHeartbeatPath = "/api/automation/lease/heartbeat";

    private readonly AutomationCommandRouter _router;
    private readonly JsonSerializerOptions _jsonOptions;
    private readonly HostRuntimeOptions _runtimeOptions;
    private readonly RunnerLeaseCoordinator _leaseCoordinator;

    internal AutomationHttpServer(
        AutomationCommandRouter router,
        JsonSerializerOptions jsonOptions,
        HostRuntimeOptions runtimeOptions,
        RunnerLeaseCoordinator leaseCoordinator)
    {
        _router = router;
        _jsonOptions = jsonOptions;
        _runtimeOptions = runtimeOptions;
        _leaseCoordinator = leaseCoordinator;
    }

    internal async Task<int> RunAsync(CancellationToken cancellationToken)
    {
        using var listener = new HttpListener();
        listener.Prefixes.Add(NormalizePrefix(_runtimeOptions.HttpPrefix));
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

        if (IsHealthPath(path))
        {
            if (!IsMethod(context.Request, "GET"))
            {
                await WriteJsonAsync(context.Response, 405, new { ok = false, error = "method_not_allowed" }, cancellationToken);
                return;
            }

            await WriteJsonAsync(context.Response, 200, BuildHealthPayload(), cancellationToken);
            return;
        }

        if (IsCapabilitiesPath(path))
        {
            if (!IsMethod(context.Request, "GET"))
            {
                await WriteJsonAsync(context.Response, 405, new { ok = false, error = "method_not_allowed" }, cancellationToken);
                return;
            }

            await WriteJsonAsync(context.Response, 200, BuildCapabilitiesPayload(), cancellationToken);
            return;
        }

        if (IsLeaseHeartbeatPath(path))
        {
            if (!IsMethod(context.Request, "POST"))
            {
                await WriteJsonAsync(context.Response, 405, new { ok = false, error = "method_not_allowed" }, cancellationToken);
                return;
            }

            if (!Authorize(context.Request))
            {
                await WriteJsonAsync(context.Response, 401, new { ok = false, error = "unauthorized" }, cancellationToken);
                return;
            }

            string owner = ResolveOwner(context.Request);
            LeaseSnapshot heartbeatSnapshot = _leaseCoordinator.Heartbeat(owner, DateTimeOffset.UtcNow);
            await WriteJsonAsync(context.Response, 200, new
            {
                ok = true,
                result = new
                {
                    owner = heartbeatSnapshot.Owner,
                    expiresAt = heartbeatSnapshot.ExpiresAt
                }
            }, cancellationToken);
            return;
        }

        bool compatibilityPath = IsCompatibilityCommandPath(path);
        if (!compatibilityPath && !IsCanonicalCommandPath(path))
        {
            await WriteJsonAsync(context.Response, 404, new { ok = false, error = "not_found" }, cancellationToken);
            return;
        }

        if (!IsMethod(context.Request, "POST"))
        {
            await WriteJsonAsync(context.Response, 405, new { ok = false, error = "method_not_allowed" }, cancellationToken);
            return;
        }

        if (!Authorize(context.Request))
        {
            await WriteJsonAsync(context.Response, 401, new { ok = false, error = "unauthorized" }, cancellationToken);
            return;
        }

        string ownerId = ResolveOwner(context.Request);
        LeaseSnapshot leaseSnapshot;
        try
        {
            leaseSnapshot = _leaseCoordinator.AcquireOrRenew(ownerId, DateTimeOffset.UtcNow);
        }
        catch (RunnerLeaseConflictException exception)
        {
            await WriteJsonAsync(context.Response, 409, new
            {
                ok = false,
                error = "lease_conflict",
                owner = exception.Owner,
                expiresAt = exception.LeaseExpiresAt
            }, cancellationToken);
            return;
        }

        string payload;
        using (var reader = new StreamReader(context.Request.InputStream, context.Request.ContentEncoding ?? Encoding.UTF8))
        {
            payload = await reader.ReadToEndAsync();
        }

        AutomationResponse response = await _router.ExecuteJsonAsync(payload, cancellationToken);
        response = response with
        {
            Metadata = MergeMetadata(
                response.Metadata,
                BuildResponseMetadata(compatibilityPath, leaseSnapshot))
        };
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

    private static bool IsMethod(HttpListenerRequest request, string method)
        => string.Equals(request.HttpMethod, method, StringComparison.OrdinalIgnoreCase);

    private static bool IsCompatibilityCommandPath(string path)
        => string.Equals(path, "/request", StringComparison.OrdinalIgnoreCase)
            || string.Equals(path, "/execute", StringComparison.OrdinalIgnoreCase);

    private static bool IsCanonicalCommandPath(string path)
        => string.Equals(path, CanonicalCommandPath, StringComparison.OrdinalIgnoreCase);

    private static bool IsCapabilitiesPath(string path)
        => string.Equals(path, CanonicalCapabilitiesPath, StringComparison.OrdinalIgnoreCase);

    private static bool IsLeaseHeartbeatPath(string path)
        => string.Equals(path, CanonicalLeaseHeartbeatPath, StringComparison.OrdinalIgnoreCase);

    private static bool IsHealthPath(string path)
        => string.Equals(path, "/health", StringComparison.OrdinalIgnoreCase)
            || string.Equals(path, CanonicalHealthPath, StringComparison.OrdinalIgnoreCase);

    private object BuildHealthPayload()
        => new
        {
            ok = true,
            service = "AgentTaskManager.AutomationHost",
            version = _runtimeOptions.ServiceVersion
        };

    private object BuildCapabilitiesPayload()
    {
        LeaseSnapshot lease = _leaseCoordinator.Snapshot(DateTimeOffset.UtcNow);
        return new
        {
            ok = true,
            result = new
            {
                service = "AgentTaskManager.AutomationHost",
                version = _runtimeOptions.ServiceVersion,
                authRequired = _runtimeOptions.AuthRequired,
                endpoints = new
                {
                    health = CanonicalHealthPath,
                    capabilities = CanonicalCapabilitiesPath,
                    command = CanonicalCommandPath,
                    leaseHeartbeat = CanonicalLeaseHeartbeatPath,
                    compatibility = new[] { "/request", "/execute" }
                },
                lease = new
                {
                    owner = lease.Owner,
                    expiresAt = lease.ExpiresAt
                },
                commands = _router.ListSupportedCommands()
            }
        };
    }

    private bool Authorize(HttpListenerRequest request)
    {
        if (!_runtimeOptions.AuthRequired)
        {
            return true;
        }

        string? authorization = request.Headers["Authorization"];
        if (string.IsNullOrWhiteSpace(authorization))
        {
            return false;
        }

        if (!authorization.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        string token = authorization["Bearer ".Length..].Trim();
        return string.Equals(token, _runtimeOptions.BearerToken, StringComparison.Ordinal);
    }

    private static string ResolveOwner(HttpListenerRequest request)
    {
        string? ownerHeader = request.Headers["X-AgentTaskManager-Runner-Owner"];
        if (!string.IsNullOrWhiteSpace(ownerHeader))
        {
            return ownerHeader.Trim();
        }

        return request.RemoteEndPoint?.ToString() ?? "anonymous";
    }

    private static IReadOnlyDictionary<string, object?> BuildResponseMetadata(bool compatibilityPath, LeaseSnapshot leaseSnapshot)
    {
        var metadata = new Dictionary<string, object?>(StringComparer.OrdinalIgnoreCase)
        {
            ["apiVersion"] = "1.0",
            ["canonicalCommandPath"] = CanonicalCommandPath,
            ["leaseOwner"] = leaseSnapshot.Owner,
            ["leaseExpiresAt"] = leaseSnapshot.ExpiresAt
        };

        if (compatibilityPath)
        {
            metadata["deprecated"] = true;
            metadata["deprecationReason"] = "Compatibility endpoint '/request' and '/execute' will be removed after one release cycle.";
        }

        return metadata;
    }

    private static IReadOnlyDictionary<string, object?> MergeMetadata(
        IReadOnlyDictionary<string, object?>? existing,
        IReadOnlyDictionary<string, object?> appended)
    {
        var merged = new Dictionary<string, object?>(StringComparer.OrdinalIgnoreCase);
        if (existing is not null)
        {
            foreach ((string key, object? value) in existing)
            {
                merged[key] = value;
            }
        }

        foreach ((string key, object? value) in appended)
        {
            merged[key] = value;
        }

        return merged;
    }
}
