using System.Text.Json;

namespace AgentTaskManager.AutomationHost;

internal sealed class AutomationCommandServer
{
    private readonly AutomationCommandRouter _router;
    private readonly JsonSerializerOptions _jsonOptions;

    internal AutomationCommandServer(AutomationCommandRouter router, JsonSerializerOptions jsonOptions)
    {
        _router = router;
        _jsonOptions = jsonOptions;
    }

    internal async Task<int> RunStdioAsync(CancellationToken cancellationToken)
    {
        while (!cancellationToken.IsCancellationRequested)
        {
            string? line = await Console.In.ReadLineAsync();
            if (line is null)
            {
                return 0;
            }

            if (string.IsNullOrWhiteSpace(line))
            {
                continue;
            }

            AutomationResponse response = await ExecuteJsonAsync(line, cancellationToken);
            await Console.Out.WriteLineAsync(JsonSerializer.Serialize(response, _jsonOptions));
            await Console.Out.FlushAsync();
        }

        return 0;
    }

    internal async Task<AutomationResponse> ExecuteJsonAsync(string json, CancellationToken cancellationToken)
        => await _router.ExecuteJsonAsync(json, cancellationToken);
}
