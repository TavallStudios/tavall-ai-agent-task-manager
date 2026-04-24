using System.Diagnostics;

namespace AgentTaskManager.AutomationHost;

internal sealed class WindowQueryService
{
    internal IReadOnlyList<WindowSummary> ListWindows(bool includeInvisible, string? titleContains = null, string? processName = null)
    {
        var windows = new List<WindowSummary>();
        NativeMethods.EnumWindows((handle, _) =>
        {
            bool isVisible = NativeMethods.IsWindowVisible(handle);
            if (!includeInvisible && !isVisible)
            {
                return true;
            }

            string title = NativeMethods.GetWindowTitle(handle);
            if (string.IsNullOrWhiteSpace(title) && string.IsNullOrWhiteSpace(titleContains))
            {
                return true;
            }

            string className = NativeMethods.GetWindowClassName(handle);
            NativeMethods.GetWindowThreadProcessId(handle, out uint processIdValue);
            int processId = unchecked((int)processIdValue);
            string resolvedProcessName = TryGetProcessName(processId);

            if (!string.IsNullOrWhiteSpace(titleContains) &&
                !title.Contains(titleContains, StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }

            if (!string.IsNullOrWhiteSpace(processName) &&
                !resolvedProcessName.Equals(processName, StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }

            if (!NativeMethods.GetWindowRect(handle, out NativeMethods.RECT rect))
            {
                return true;
            }

            windows.Add(new WindowSummary(
                Handle: handle.ToInt64(),
                HandleHex: $"0x{handle.ToInt64():X}",
                Title: title,
                ClassName: className,
                ProcessId: processId,
                ProcessName: resolvedProcessName,
                IsVisible: isVisible,
                IsMinimized: NativeMethods.IsIconic(handle),
                Bounds: ToWindowRect(rect)));
            return true;
        }, IntPtr.Zero);

        return windows
            .OrderBy(window => window.ProcessName, StringComparer.OrdinalIgnoreCase)
            .ThenBy(window => window.Title, StringComparer.OrdinalIgnoreCase)
            .ToArray();
    }

    internal WindowSummary ResolveWindow(WindowTarget target, bool includeInvisible = false)
    {
        if (target.Handle.HasValue)
        {
            IntPtr handle = new(target.Handle.Value);
            if (!NativeMethods.GetWindowRect(handle, out NativeMethods.RECT rect))
            {
                throw new InvalidOperationException($"Window handle was not found: 0x{target.Handle.Value:X}");
            }

            NativeMethods.GetWindowThreadProcessId(handle, out uint processIdValue);
            int processId = unchecked((int)processIdValue);
            return new WindowSummary(
                Handle: handle.ToInt64(),
                HandleHex: $"0x{handle.ToInt64():X}",
                Title: NativeMethods.GetWindowTitle(handle),
                ClassName: NativeMethods.GetWindowClassName(handle),
                ProcessId: processId,
                ProcessName: TryGetProcessName(processId),
                IsVisible: NativeMethods.IsWindowVisible(handle),
                IsMinimized: NativeMethods.IsIconic(handle),
                Bounds: ToWindowRect(rect));
        }

        WindowSummary? match = ListWindows(includeInvisible, target.TitleContains, target.ProcessName)
            .OrderByDescending(window => window.IsVisible)
            .ThenByDescending(window => !string.IsNullOrWhiteSpace(window.Title))
            .ThenByDescending(window => window.Bounds.Width * window.Bounds.Height)
            .FirstOrDefault();
        return match ?? throw new InvalidOperationException("No matching window was found.");
    }

    internal static WindowRect ToWindowRect(NativeMethods.RECT rect)
        => new(
            Left: rect.Left,
            Top: rect.Top,
            Width: Math.Max(0, rect.Right - rect.Left),
            Height: Math.Max(0, rect.Bottom - rect.Top));

    private static string TryGetProcessName(int processId)
    {
        try
        {
            return Process.GetProcessById(processId).ProcessName;
        }
        catch
        {
            return string.Empty;
        }
    }
}
