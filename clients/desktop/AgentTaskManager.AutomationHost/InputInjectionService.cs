using System.Windows.Input;

namespace AgentTaskManager.AutomationHost;

internal sealed class InputInjectionService
{
    private readonly InputBatchInjectionService _batchInjectionService = new();
    private readonly WindowMessageInputService _windowMessageInputService = new();

    internal object Focus(WindowSummary window, bool restoreIfMinimized)
    {
        IntPtr handle = new(window.Handle);
        if (restoreIfMinimized && window.IsMinimized)
        {
            NativeMethods.ShowWindow(handle, NativeMethods.SwRestore);
        }

        bool focused = NativeMethods.SetForegroundWindow(handle);
        return new
        {
            window.Handle,
            focused,
            intrusive = false
        };
    }

    internal object SendText(WindowSummary window, string text, bool activateWindow)
    {
        if (!activateWindow)
        {
            return _windowMessageInputService.SendText(window, text);
        }

        if (activateWindow)
        {
            Focus(window, restoreIfMinimized: true);
        }

        foreach (char character in text)
        {
            SendUnicodeChar(character);
        }

        return new
        {
            window.Handle,
            intrusive = activateWindow,
            sentLength = text.Length
        };
    }

    internal object Click(WindowSummary window, int x, int y, string mode, bool activateWindow)
    {
        IntPtr handle = new(window.Handle);
        string normalizedMode = mode.Equals("sendInput", StringComparison.OrdinalIgnoreCase)
            ? "sendInput"
            : "windowMessage";

        if (normalizedMode == "windowMessage")
        {
            IntPtr lParam = NativeMethods.MakeLParam(x, y);
            NativeMethods.PostMessage(handle, NativeMethods.WmMouseMove, IntPtr.Zero, lParam);
            NativeMethods.PostMessage(handle, NativeMethods.WmLButtonDown, (IntPtr)NativeMethods.MkLButton, lParam);
            NativeMethods.PostMessage(handle, NativeMethods.WmLButtonUp, IntPtr.Zero, lParam);
            return new
            {
                window.Handle,
                mode = normalizedMode,
                intrusive = false
            };
        }

        if (activateWindow)
        {
            Focus(window, restoreIfMinimized: true);
        }

        if (!TryToScreenPoint(handle, x, y, out NativeMethods.POINT screenPoint))
        {
            throw new InvalidOperationException("Failed to convert the requested client point to screen coordinates.");
        }

        NativeMethods.SetCursorPos(screenPoint.X, screenPoint.Y);
        NativeMethods.SendInput(2, new[]
        {
            new NativeMethods.INPUT
            {
                type = NativeMethods.InputMouse,
                U = new NativeMethods.InputUnion
                {
                    mi = new NativeMethods.MOUSEINPUT { dwFlags = NativeMethods.MouseeventfLeftDown }
                }
            },
            new NativeMethods.INPUT
            {
                type = NativeMethods.InputMouse,
                U = new NativeMethods.InputUnion
                {
                    mi = new NativeMethods.MOUSEINPUT { dwFlags = NativeMethods.MouseeventfLeftUp }
                }
            }
        }, System.Runtime.InteropServices.Marshal.SizeOf<NativeMethods.INPUT>());
        return new
        {
            window.Handle,
            mode = normalizedMode,
            intrusive = true,
            activated = activateWindow
        };
    }

    internal Task<object> SendKeyBatchAsync(WindowSummary? window, IReadOnlyList<KeyBatchEvent> events, bool activateWindow, CancellationToken cancellationToken)
        => _batchInjectionService.SendKeyBatchAsync(this, window, events, activateWindow, cancellationToken);

    internal Task<object> SendMouseBatchAsync(WindowSummary? window, IReadOnlyList<MouseBatchEvent> events, bool activateWindow, CancellationToken cancellationToken)
        => _batchInjectionService.SendMouseBatchAsync(this, window, events, activateWindow, cancellationToken);

    private static bool TryToScreenPoint(IntPtr handle, int x, int y, out NativeMethods.POINT screenPoint)
    {
        screenPoint = new NativeMethods.POINT { X = x, Y = y };
        return NativeMethods.ClientToScreen(handle, ref screenPoint);
    }


    private static void SendUnicodeChar(char character)
    {
        var inputs = new[]
        {
            new NativeMethods.INPUT
            {
                type = NativeMethods.InputKeyboard,
                U = new NativeMethods.InputUnion
                {
                    ki = new NativeMethods.KEYBDINPUT
                    {
                        wScan = character,
                        dwFlags = NativeMethods.KeyeventfUnicode
                    }
                }
            },
            new NativeMethods.INPUT
            {
                type = NativeMethods.InputKeyboard,
                U = new NativeMethods.InputUnion
                {
                    ki = new NativeMethods.KEYBDINPUT
                    {
                        wScan = character,
                        dwFlags = NativeMethods.KeyeventfUnicode | NativeMethods.KeyeventfKeyUp
                    }
                }
            }
        };
        NativeMethods.SendInput((uint)inputs.Length, inputs, System.Runtime.InteropServices.Marshal.SizeOf<NativeMethods.INPUT>());
    }
}
