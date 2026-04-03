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
            AutomationHttpServer httpServer = new(router, jsonOptions);
            return await httpServer.RunAsync(httpPrefix, CancellationToken.None);
        }

        return await commandServer.RunStdioAsync(CancellationToken.None);
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
