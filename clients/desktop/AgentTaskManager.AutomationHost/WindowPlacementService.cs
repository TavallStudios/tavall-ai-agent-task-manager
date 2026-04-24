namespace AgentTaskManager.AutomationHost;

internal sealed class WindowPlacementService
{
    internal object Move(WindowSummary window, int left, int top, int width, int height, bool restoreIfMinimized, bool activateWindow)
    {
        IntPtr handle = new(window.Handle);
        if (restoreIfMinimized && window.IsMinimized)
        {
            NativeMethods.ShowWindow(handle, NativeMethods.SwRestore);
        }

        uint flags = NativeMethods.SwpNoZorder | NativeMethods.SwpNoOwnerZorder;
        if (!activateWindow)
        {
            flags |= NativeMethods.SwpNoActivate;
        }

        bool moved = NativeMethods.SetWindowPos(
            handle,
            IntPtr.Zero,
            left,
            top,
            Math.Max(1, width),
            Math.Max(1, height),
            flags);
        if (!moved)
        {
            throw new InvalidOperationException("Failed to move the requested window.");
        }

        bool focused = false;
        if (activateWindow)
        {
            focused = NativeMethods.SetForegroundWindow(handle);
        }

        return new
        {
            window.Handle,
            moved,
            focused,
            intrusive = activateWindow,
            bounds = new WindowRect(left, top, Math.Max(1, width), Math.Max(1, height))
        };
    }
}
