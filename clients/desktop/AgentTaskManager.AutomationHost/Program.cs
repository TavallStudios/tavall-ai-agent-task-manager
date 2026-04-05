using System.Text.Json;

namespace AgentTaskManager.AutomationHost;

internal static class Program
{
    [STAThread]
    private static async Task<int> Main(string[] args)
    {
        var jsonOptions = new JsonSerializerOptions
        {
            PropertyNameCaseInsensitive = true,
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
            WriteIndented = false
        };

        AutomationCommandRouter router = new(jsonOptions);
        AutomationCommandServer commandServer = new(router, jsonOptions);

        if (TryGetArgumentValue(args, "--request") is string requestJson)
        {
            AutomationResponse response = await commandServer.ExecuteJsonAsync(requestJson, CancellationToken.None);
            await Console.Out.WriteLineAsync(JsonSerializer.Serialize(response, jsonOptions));
            return response.Ok ? 0 : 1;
        }

        if (TryGetHttpPrefix(args) is string httpPrefix)
        {
            HostRuntimeOptions options = BuildRuntimeOptions(args, httpPrefix);
            RunnerLeaseCoordinator leaseCoordinator = new(TimeSpan.FromSeconds(options.LeaseTtlSeconds));
            AutomationHttpServer httpServer = new(router, jsonOptions, options, leaseCoordinator);
            return await httpServer.RunAsync(CancellationToken.None);
        }

        return await commandServer.RunStdioAsync(CancellationToken.None);
    }

    private static HostRuntimeOptions BuildRuntimeOptions(string[] args, string httpPrefix)
    {
        string? bearerToken = TryGetArgumentValue(args, "--auth-token");
        string? leaseTtlText = TryGetArgumentValue(args, "--lease-ttl-seconds");
        int leaseTtlSeconds = int.TryParse(leaseTtlText, out int parsedLeaseTtl) && parsedLeaseTtl > 0
            ? parsedLeaseTtl
            : 60;
        string serviceVersion = TryGetArgumentValue(args, "--service-version") ?? "1.0";
        return new HostRuntimeOptions(
            httpPrefix,
            string.IsNullOrWhiteSpace(bearerToken) ? null : bearerToken.Trim(),
            leaseTtlSeconds,
            serviceVersion);
    }

    private static string? TryGetHttpPrefix(string[] args)
    {
        string? explicitPrefix = TryGetArgumentValue(args, "--http-prefix");
        if (!string.IsNullOrWhiteSpace(explicitPrefix))
        {
            return explicitPrefix;
        }

        string? portText = TryGetArgumentValue(args, "--http-port");
        if (int.TryParse(portText, out int port) && port > 0)
        {
            return $"http://127.0.0.1:{port}/";
        }

        return args.Any(argument => argument.Equals("--http", StringComparison.OrdinalIgnoreCase))
            ? "http://127.0.0.1:54123/"
            : null;
    }

    private static string? TryGetArgumentValue(IEnumerable<string> args, string name)
    {
        string prefix = name + "=";
        foreach (string argument in args)
        {
            if (argument.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
            {
                return argument[prefix.Length..];
            }
        }

        string[] array = args as string[] ?? args.ToArray();
        for (int index = 0; index < array.Length - 1; index++)
        {
            if (array[index].Equals(name, StringComparison.OrdinalIgnoreCase))
            {
                return array[index + 1];
            }
        }

        return null;
    }
}
