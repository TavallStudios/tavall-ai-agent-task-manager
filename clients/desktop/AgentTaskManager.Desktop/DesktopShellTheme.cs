using Microsoft.UI;
using Microsoft.UI.Xaml.Media;
using Windows.UI.Text;

namespace AgentTaskManager.Desktop;

internal static class DesktopShellTheme
{
    internal static readonly SolidColorBrush ShellBackground = Brush(0xFF, 0x09, 0x0C, 0x10);
    internal static readonly SolidColorBrush SidebarBackground = Brush(0xFF, 0x0E, 0x12, 0x18);
    internal static readonly SolidColorBrush SidebarBorder = Brush(0xFF, 0x1A, 0x22, 0x2B);
    internal static readonly SolidColorBrush PageBackground = Brush(0x00, 0x00, 0x00, 0x00);
    internal static readonly SolidColorBrush CardBackground = Brush(0xFF, 0x11, 0x16, 0x1D);
    internal static readonly SolidColorBrush RaisedCardBackground = Brush(0xFF, 0x15, 0x1C, 0x24);
    internal static readonly SolidColorBrush CardBorder = Brush(0xFF, 0x22, 0x2C, 0x36);
    internal static readonly SolidColorBrush Accent = Brush(0xFF, 0x28, 0xC2, 0x81);
    internal static readonly SolidColorBrush AccentMuted = Brush(0xFF, 0x1A, 0x72, 0x55);
    internal static readonly SolidColorBrush AccentSurface = Brush(0x33, 0x28, 0xC2, 0x81);
    internal static readonly SolidColorBrush NavSelectedBackground = Brush(0xFF, 0x19, 0x22, 0x2B);
    internal static readonly SolidColorBrush NavHoverBorder = Brush(0xFF, 0x31, 0x3D, 0x49);
    internal static readonly SolidColorBrush TextPrimary = Brush(0xFF, 0xF4, 0xF7, 0xFA);
    internal static readonly SolidColorBrush TextSecondary = Brush(0xFF, 0xB7, 0xC2, 0xCC);
    internal static readonly SolidColorBrush TextMuted = Brush(0xFF, 0x7D, 0x89, 0x96);
    internal static readonly SolidColorBrush TextOnAccent = Brush(0xFF, 0x0B, 0x0F, 0x14);
    internal static readonly SolidColorBrush InputBackground = Brush(0xFF, 0x0E, 0x14, 0x1A);
    internal static readonly SolidColorBrush InputBorder = Brush(0xFF, 0x2B, 0x37, 0x42);
    internal static readonly SolidColorBrush MonoBackground = Brush(0xFF, 0x0B, 0x11, 0x17);
    internal static readonly SolidColorBrush GoodAccent = Brush(0xFF, 0x31, 0xD0, 0x8A);
    internal static readonly SolidColorBrush WarningAccent = Brush(0xFF, 0xF2, 0xB1, 0x34);
    internal static readonly SolidColorBrush DangerAccent = Brush(0xFF, 0xF0, 0x66, 0x66);

    internal static readonly FontWeight MediumWeight = new() { Weight = 500 };
    internal static readonly FontWeight SemiBoldWeight = new() { Weight = 600 };
    internal static readonly FontWeight BoldWeight = new() { Weight = 700 };
    internal static readonly FontFamily DisplayFont = new("Segoe UI Variable Display");
    internal static readonly FontFamily BodyFont = new("Segoe UI Variable Text");
    internal static readonly FontFamily MonoFont = new("Cascadia Code");

    private static SolidColorBrush Brush(byte a, byte r, byte g, byte b)
        => new(ColorHelper.FromArgb(a, r, g, b));
}
