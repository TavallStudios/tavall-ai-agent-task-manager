using System.Diagnostics;

namespace AgentTaskManager.Desktop.Services;

internal static class CodexProcessStartInfoFactory
{
    public static ProcessStartInfo Build(
        string commandPath,
        string workingDirectory,
        IReadOnlyList<string> arguments,
        IReadOnlyDictionary<string, string> environment,
        bool createNoWindow,
        bool redirectOutput = false)
    {
        bool isCmd = commandPath.EndsWith(".cmd", StringComparison.OrdinalIgnoreCase);
        bool isPowerShell = commandPath.EndsWith(".ps1", StringComparison.OrdinalIgnoreCase);
        var startInfo = new ProcessStartInfo
        {
            WorkingDirectory = workingDirectory,
            UseShellExecute = false,
            CreateNoWindow = createNoWindow,
            RedirectStandardOutput = redirectOutput,
            RedirectStandardError = redirectOutput
        };

        if (isCmd)
        {
            startInfo.FileName = Environment.GetEnvironmentVariable("ComSpec") ?? "cmd.exe";
            startInfo.Arguments = $"/c \"\"{commandPath}\" {string.Join(" ", arguments.Select(QuoteArgument))}\"";
        }
        else if (isPowerShell)
        {
            startInfo.FileName = "powershell.exe";
            startInfo.Arguments = $"-NoProfile -ExecutionPolicy Bypass -File {QuoteArgument(commandPath)} {string.Join(" ", arguments.Select(QuoteArgument))}";
        }
        else
        {
            startInfo.FileName = commandPath;
            startInfo.Arguments = string.Join(" ", arguments.Select(QuoteArgument));
        }

        foreach ((string key, string value) in environment)
        {
            startInfo.Environment[key] = value;
        }

        return startInfo;
    }

    private static string QuoteArgument(string value)
        => value.Contains(' ') || value.Contains('"')
            ? $"\"{value.Replace("\"", "\\\"")}\""
            : value;
}
