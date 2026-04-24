using System.Text;

namespace AgentTaskManager.Desktop.Services;

public static class DesktopDiagnosticsLog
{
    private static readonly object Gate = new();

    public static string LogFilePath => Path.Combine(DesktopStoragePaths.RootDirectory, "desktop.log");

    public static void WriteInfo(string message)
        => Write("INFO", message, exception: null);

    public static void WriteError(string message, Exception? exception = null)
        => Write("ERROR", message, exception);

    private static void Write(string level, string message, Exception? exception)
    {
        try
        {
            DesktopStoragePaths.EnsureCreated();
            var builder = new StringBuilder()
                .Append(DateTimeOffset.Now.ToString("O"))
                .Append(" [")
                .Append(level)
                .Append("] ")
                .AppendLine(message);
            if (exception != null)
            {
                builder.AppendLine(exception.ToString());
            }

            lock (Gate)
            {
                File.AppendAllText(LogFilePath, builder.ToString(), Encoding.UTF8);
            }
        }
        catch
        {
        }
    }
}
