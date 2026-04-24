using System.Windows.Input;

namespace AgentTaskManager.AutomationHost;

internal sealed class InputBatchInjectionService
{
    private readonly WindowMessageInputService _windowMessageInputService = new();
    internal async Task<object> SendKeyBatchAsync(InputInjectionService inputService, WindowSummary? window, IReadOnlyList<KeyBatchEvent> events, bool activateWindow, CancellationToken cancellationToken)
    {
        ValidateBatch(events, "key");
        if (window is not null && !activateWindow)
        {
            await _windowMessageInputService.SendKeyBatchAsync(window, events, cancellationToken);
            return new { windowHandle = window.Handle, delivery = "windowMessage", intrusive = false, sentCount = events.Count };
        }

        if (window is not null && activateWindow)
        {
            inputService.Focus(window, restoreIfMinimized: true);
        }

        foreach (KeyBatchEvent batchEvent in events)
        {
            SendKeyEvent(batchEvent);
            if (batchEvent.DelayMs > 0)
            {
                await Task.Delay(batchEvent.DelayMs, cancellationToken);
            }
        }

        return new
        {
            windowHandle = window?.Handle,
            intrusive = window is not null && activateWindow,
            sentCount = events.Count
        };
    }

    internal async Task<object> SendMouseBatchAsync(InputInjectionService inputService, WindowSummary? window, IReadOnlyList<MouseBatchEvent> events, bool activateWindow, CancellationToken cancellationToken)
    {
        ValidateBatch(events, "mouse");
        if (window is not null && !activateWindow)
        {
            await _windowMessageInputService.SendMouseBatchAsync(window, events, cancellationToken);
            return new { windowHandle = window.Handle, delivery = "windowMessage", intrusive = false, sentCount = events.Count };
        }

        if (window is not null && activateWindow)
        {
            inputService.Focus(window, restoreIfMinimized: true);
        }

        foreach (MouseBatchEvent batchEvent in events)
        {
            SendMouseEvent(window, batchEvent);
            if (batchEvent.DelayMs > 0)
            {
                await Task.Delay(batchEvent.DelayMs, cancellationToken);
            }
        }

        return new
        {
            windowHandle = window?.Handle,
            intrusive = window is not null && activateWindow,
            sentCount = events.Count
        };
    }

    private static void SendKeyEvent(KeyBatchEvent batchEvent)
    {
        string action = NormalizeAction(batchEvent.Action);
        if (action is not "press" and not "down" and not "up")
        {
            throw new InvalidOperationException($"Unsupported key batch action: {batchEvent.Action}");
        }

        ushort virtualKey = ResolveVirtualKey(batchEvent);
        ushort scanCode = ResolveScanCode(virtualKey, batchEvent.ScanCode);
        if (action == "press")
        {
            SendInputKey(virtualKey, scanCode, batchEvent.Extended, isKeyUp: false);
            SendInputKey(virtualKey, scanCode, batchEvent.Extended, isKeyUp: true);
            return;
        }

        SendInputKey(virtualKey, scanCode, batchEvent.Extended, isKeyUp: action == "up");
    }

    private static void SendInputKey(ushort virtualKey, ushort scanCode, bool extended, bool isKeyUp)
    {
        bool useScanCode = scanCode != 0;
        var input = new NativeMethods.INPUT
        {
            type = NativeMethods.InputKeyboard,
            U = new NativeMethods.InputUnion
            {
                ki = new NativeMethods.KEYBDINPUT
                {
                    wVk = useScanCode ? (ushort)0 : virtualKey,
                    wScan = useScanCode ? scanCode : virtualKey,
                    dwFlags = isKeyUp ? NativeMethods.KeyeventfKeyUp : 0
                }
            }
        };

        if (useScanCode)
        {
            input.U.ki.dwFlags |= NativeMethods.KeyeventfScancode;
        }

        if (extended)
        {
            input.U.ki.dwFlags |= NativeMethods.KeyeventfExtendedKey;
        }

        NativeMethods.SendInput(1, new[] { input }, System.Runtime.InteropServices.Marshal.SizeOf<NativeMethods.INPUT>());
    }

    private static ushort ResolveVirtualKey(KeyBatchEvent batchEvent)
    {
        if (batchEvent.VirtualKey.HasValue)
        {
            return checked((ushort)batchEvent.VirtualKey.Value);
        }

        if (batchEvent.ScanCode.HasValue)
        {
            return 0;
        }

        if (!string.IsNullOrWhiteSpace(batchEvent.Key))
        {
            return (ushort)ResolveKey(batchEvent.Key);
        }

        throw new InvalidOperationException("A key batch event must specify Key, VirtualKey, or ScanCode.");
    }

    private static ushort ResolveScanCode(ushort virtualKey, int? scanCode)
    {
        if (scanCode.HasValue)
        {
            return checked((ushort)scanCode.Value);
        }

        if (virtualKey == 0)
        {
            return 0;
        }

        return checked((ushort)NativeMethods.MapVirtualKey(virtualKey, 0));
    }

    private static Key ResolveKey(string key)
    {
        if (Enum.TryParse<Key>(key, true, out Key parsed))
        {
            return parsed;
        }

        if (key.Length == 1)
        {
            char character = key[0];
            if (char.IsLetter(character))
            {
                return Enum.Parse<Key>(character.ToString().ToUpperInvariant(), true);
            }

            if (char.IsDigit(character))
            {
                return Enum.Parse<Key>($"D{character}", true);
            }
        }

        throw new InvalidOperationException($"Unsupported key name: {key}");
    }

    private static void SendMouseEvent(WindowSummary? window, MouseBatchEvent batchEvent)
    {
        string action = NormalizeAction(batchEvent.Action);
        string coordinates = NormalizeAction(batchEvent.Coordinates);
        if (coordinates is not "screen" and not "client")
        {
            throw new InvalidOperationException($"Unsupported mouse coordinate space: {batchEvent.Coordinates}");
        }

        NativeMethods.POINT point = ResolvePoint(window, batchEvent.X, batchEvent.Y, coordinates);
        if (action == "move")
        {
            NativeMethods.SetCursorPos(point.X, point.Y);
            return;
        }

        if (action is "click" or "doubleclick")
        {
            NativeMethods.SetCursorPos(point.X, point.Y);
            SendMouseButtonEvent(batchEvent.Button, isDown: true);
            SendMouseButtonEvent(batchEvent.Button, isDown: false);
            if (action == "doubleclick")
            {
                SendMouseButtonEvent(batchEvent.Button, isDown: true);
                SendMouseButtonEvent(batchEvent.Button, isDown: false);
            }

            return;
        }

        if (action is "down" or "up")
        {
            NativeMethods.SetCursorPos(point.X, point.Y);
            SendMouseButtonEvent(batchEvent.Button, isDown: action == "down");
            return;
        }

        if (action == "wheel")
        {
            SendMouseWheel(batchEvent.WheelDelta);
            return;
        }

        throw new InvalidOperationException($"Unsupported mouse batch action: {batchEvent.Action}");
    }

    private static NativeMethods.POINT ResolvePoint(WindowSummary? window, int x, int y, string coordinates)
    {
        if (coordinates == "client")
        {
            if (window is null)
            {
                throw new InvalidOperationException("Client coordinates require a target window.");
            }

            IntPtr handle = new(window.Handle);
            NativeMethods.POINT point = new() { X = x, Y = y };
            if (!NativeMethods.ClientToScreen(handle, ref point))
            {
                throw new InvalidOperationException("Failed to translate client coordinates to screen coordinates.");
            }

            return point;
        }

        return new NativeMethods.POINT { X = x, Y = y };
    }

    private static void SendMouseButtonEvent(string button, bool isDown)
    {
        uint flags = button.ToLowerInvariant() switch
        {
            "left" => isDown ? NativeMethods.MouseeventfLeftDown : NativeMethods.MouseeventfLeftUp,
            "right" => isDown ? NativeMethods.MouseeventfRightDown : NativeMethods.MouseeventfRightUp,
            "middle" => isDown ? NativeMethods.MouseeventfMiddleDown : NativeMethods.MouseeventfMiddleUp,
            _ => throw new InvalidOperationException($"Unsupported mouse button: {button}")
        };

        NativeMethods.SendInput(1, new[]
        {
            new NativeMethods.INPUT
            {
                type = NativeMethods.InputMouse,
                U = new NativeMethods.InputUnion
                {
                    mi = new NativeMethods.MOUSEINPUT { dwFlags = flags }
                }
            }
        }, System.Runtime.InteropServices.Marshal.SizeOf<NativeMethods.INPUT>());
    }

    private static void SendMouseWheel(int wheelDelta)
    {
        NativeMethods.SendInput(1, new[]
        {
            new NativeMethods.INPUT
            {
                type = NativeMethods.InputMouse,
                U = new NativeMethods.InputUnion
                {
                    mi = new NativeMethods.MOUSEINPUT
                    {
                        mouseData = unchecked((uint)wheelDelta),
                        dwFlags = NativeMethods.MouseeventfWheel
                    }
                }
            }
        }, System.Runtime.InteropServices.Marshal.SizeOf<NativeMethods.INPUT>());
    }

    private static string NormalizeAction(string action)
        => action.Trim().ToLowerInvariant();

    private static void ValidateBatch<T>(IReadOnlyList<T> events, string kind)
    {
        if (events.Count == 0)
        {
            throw new InvalidOperationException($"The {kind} batch was empty.");
        }
    }
}
