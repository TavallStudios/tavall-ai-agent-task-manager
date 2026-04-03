using System.Windows.Input;

namespace AgentTaskManager.AutomationHost;

internal sealed class WindowMessageInputService
{
    internal object SendText(WindowSummary window, string text)
    {
        IntPtr handle = new(window.Handle);
        foreach (char character in text)
        {
            NativeMethods.PostMessage(handle, NativeMethods.WmChar, (IntPtr)character, IntPtr.Zero);
        }

        return new
        {
            window.Handle,
            delivery = "windowMessage",
            intrusive = false,
            sentLength = text.Length
        };
    }

    internal async Task SendKeyBatchAsync(WindowSummary window, IReadOnlyList<KeyBatchEvent> events, CancellationToken cancellationToken)
    {
        IntPtr handle = new(window.Handle);
        foreach (KeyBatchEvent batchEvent in events)
        {
            PostKeyEvent(handle, batchEvent);
            if (batchEvent.DelayMs > 0)
            {
                await Task.Delay(batchEvent.DelayMs, cancellationToken);
            }
        }
    }

    internal async Task SendMouseBatchAsync(WindowSummary window, IReadOnlyList<MouseBatchEvent> events, CancellationToken cancellationToken)
    {
        IntPtr handle = new(window.Handle);
        foreach (MouseBatchEvent batchEvent in events)
        {
            PostMouseEvent(handle, window, batchEvent);
            if (batchEvent.DelayMs > 0)
            {
                await Task.Delay(batchEvent.DelayMs, cancellationToken);
            }
        }
    }

    private static void PostKeyEvent(IntPtr handle, KeyBatchEvent batchEvent)
    {
        string action = Normalize(batchEvent.Action);
        bool isPress = action == "press";
        bool isDown = action == "down";
        bool isUp = action == "up";
        if (!isPress && !isDown && !isUp)
        {
            throw new InvalidOperationException($"Unsupported key batch action: {batchEvent.Action}");
        }

        ushort virtualKey = ResolveVirtualKey(batchEvent);
        ushort scanCode = ResolveScanCode(virtualKey, batchEvent.ScanCode);
        if (isPress || isDown)
        {
            NativeMethods.PostMessage(handle, NativeMethods.WmKeyDown, (IntPtr)virtualKey, BuildKeyLParam(scanCode, batchEvent.Extended, isKeyUp: false));
            if (isPress && TryResolveCharacter(batchEvent.Key, out char character))
            {
                NativeMethods.PostMessage(handle, NativeMethods.WmChar, (IntPtr)character, BuildKeyLParam(scanCode, batchEvent.Extended, isKeyUp: false));
            }
        }

        if (isPress || isUp)
        {
            NativeMethods.PostMessage(handle, NativeMethods.WmKeyUp, (IntPtr)virtualKey, BuildKeyLParam(scanCode, batchEvent.Extended, isKeyUp: true));
        }
    }

    private static void PostMouseEvent(IntPtr handle, WindowSummary window, MouseBatchEvent batchEvent)
    {
        string action = Normalize(batchEvent.Action);
        NativeMethods.POINT clientPoint = ResolveClientPoint(handle, batchEvent);
        IntPtr clientLParam = NativeMethods.MakeLParam(clientPoint.X, clientPoint.Y);

        if (action == "move")
        {
            NativeMethods.PostMessage(handle, NativeMethods.WmMouseMove, IntPtr.Zero, clientLParam);
            return;
        }

        if (action == "wheel")
        {
            NativeMethods.POINT screenPoint = ToScreenPoint(handle, clientPoint);
            int wheelData = (short)batchEvent.WheelDelta;
            IntPtr wParam = (IntPtr)(wheelData << 16);
            IntPtr lParam = NativeMethods.MakeLParam(screenPoint.X, screenPoint.Y);
            NativeMethods.PostMessage(handle, NativeMethods.WmMouseWheel, wParam, lParam);
            return;
        }

        bool isClick = action == "click";
        bool isDoubleClick = action == "doubleclick";
        bool isDown = action == "down";
        bool isUp = action == "up";
        if (!isClick && !isDoubleClick && !isDown && !isUp)
        {
            throw new InvalidOperationException($"Unsupported mouse batch action: {batchEvent.Action}");
        }

        NativeMethods.PostMessage(handle, NativeMethods.WmMouseMove, IntPtr.Zero, clientLParam);
        if (isClick || isDoubleClick || isDown)
        {
            PostMouseButton(handle, batchEvent.Button, isDown: true, clientLParam);
        }

        if (isClick || isDoubleClick || isUp)
        {
            PostMouseButton(handle, batchEvent.Button, isDown: false, clientLParam);
        }

        if (isDoubleClick)
        {
            PostMouseButton(handle, batchEvent.Button, isDown: true, clientLParam);
            PostMouseButton(handle, batchEvent.Button, isDown: false, clientLParam);
        }
    }

    private static void PostMouseButton(IntPtr handle, string button, bool isDown, IntPtr lParam)
    {
        (uint message, uint flags) = Normalize(button) switch
        {
            "left" => (isDown ? NativeMethods.WmLButtonDown : NativeMethods.WmLButtonUp, NativeMethods.MkLButton),
            "right" => (isDown ? NativeMethods.WmRButtonDown : NativeMethods.WmRButtonUp, NativeMethods.MkRButton),
            "middle" => (isDown ? NativeMethods.WmMButtonDown : NativeMethods.WmMButtonUp, NativeMethods.MkMButton),
            _ => throw new InvalidOperationException($"Unsupported mouse button: {button}")
        };
        NativeMethods.PostMessage(handle, message, isDown ? (IntPtr)flags : IntPtr.Zero, lParam);
    }

    private static NativeMethods.POINT ResolveClientPoint(IntPtr handle, MouseBatchEvent batchEvent)
    {
        NativeMethods.POINT point = new() { X = batchEvent.X, Y = batchEvent.Y };
        if (Normalize(batchEvent.Coordinates) == "client")
        {
            return point;
        }

        if (Normalize(batchEvent.Coordinates) != "screen")
        {
            throw new InvalidOperationException($"Unsupported mouse coordinate space: {batchEvent.Coordinates}");
        }

        if (!NativeMethods.ScreenToClient(handle, ref point))
        {
            throw new InvalidOperationException("Failed to translate screen coordinates to client coordinates.");
        }

        return point;
    }

    private static NativeMethods.POINT ToScreenPoint(IntPtr handle, NativeMethods.POINT clientPoint)
    {
        NativeMethods.POINT screenPoint = clientPoint;
        if (!NativeMethods.ClientToScreen(handle, ref screenPoint))
        {
            throw new InvalidOperationException("Failed to translate client coordinates to screen coordinates.");
        }

        return screenPoint;
    }

    private static IntPtr BuildKeyLParam(ushort scanCode, bool extended, bool isKeyUp)
    {
        int lParam = 1 | (scanCode << 16);
        if (extended)
        {
            lParam |= 1 << 24;
        }

        if (isKeyUp)
        {
            lParam |= 1 << 30;
            lParam |= 1 << 31;
        }

        return (IntPtr)lParam;
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
            return (ushort)KeyInterop.VirtualKeyFromKey(ResolveKey(batchEvent.Key));
        }

        throw new InvalidOperationException("A key batch event must specify Key, VirtualKey, or ScanCode.");
    }

    private static ushort ResolveScanCode(ushort virtualKey, int? scanCode)
    {
        if (scanCode.HasValue)
        {
            return checked((ushort)scanCode.Value);
        }

        return virtualKey == 0 ? (ushort)0 : checked((ushort)NativeMethods.MapVirtualKey(virtualKey, 0));
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

    private static bool TryResolveCharacter(string? key, out char character)
    {
        character = default;
        return !string.IsNullOrWhiteSpace(key) && key.Length == 1 && (character = key[0]) != default;
    }

    private static string Normalize(string value)
        => value.Trim().ToLowerInvariant();
}
