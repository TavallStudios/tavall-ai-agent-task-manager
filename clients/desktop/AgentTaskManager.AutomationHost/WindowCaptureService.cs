using System.Drawing;
using System.Drawing.Imaging;
using System.IO;

namespace AgentTaskManager.AutomationHost;

internal sealed class WindowCaptureService
{
    internal CaptureResult Capture(WindowSummary window, string? outputPath, bool allowScreenCopyFallback)
    {
        IntPtr handle = new(window.Handle);
        if (window.Bounds.Width <= 0 || window.Bounds.Height <= 0)
        {
            throw new InvalidOperationException("The target window has no visible bounds to capture.");
        }

        string resolvedPath = ResolveOutputPath(outputPath, BuildWindowFileName(window));
        using var bitmap = new Bitmap(window.Bounds.Width, window.Bounds.Height, PixelFormat.Format32bppArgb);
        string captureMode = "printWindow";
        bool printed = TryPrintWindow(handle, bitmap);
        if (!printed)
        {
            if (!allowScreenCopyFallback)
            {
                throw new InvalidOperationException("PrintWindow failed and screen-copy fallback was disabled.");
            }

            captureMode = "copyFromScreen";
            CopyFromScreen(bitmap, new Rectangle(window.Bounds.Left, window.Bounds.Top, window.Bounds.Width, window.Bounds.Height));
        }

        bitmap.Save(resolvedPath, ImageFormat.Png);
        return new CaptureResult(resolvedPath, captureMode, window.Bounds);
    }

    internal CaptureResult CaptureRegion(WindowRect region, string? outputPath, bool allowScreenCopyFallback)
    {
        ValidateRegion(region);
        string resolvedPath = ResolveOutputPath(outputPath, $"region-{region.Left}-{region.Top}-{region.Width}x{region.Height}.png");
        using var bitmap = new Bitmap(region.Width, region.Height, PixelFormat.Format32bppArgb);
        CopyFromScreen(bitmap, new Rectangle(region.Left, region.Top, region.Width, region.Height), allowScreenCopyFallback);
        bitmap.Save(resolvedPath, ImageFormat.Png);
        return new CaptureResult(resolvedPath, "copyFromScreen", region);
    }

    internal CaptureResult CaptureStreamFrame(WindowSummary? window, WindowRect? region, string? outputPath, bool allowScreenCopyFallback)
    {
        if (window is not null)
        {
            return region is null
                ? Capture(window, outputPath, allowScreenCopyFallback)
                : CaptureWindowRegion(window, region, outputPath, allowScreenCopyFallback);
        }

        if (region is not null)
        {
            return CaptureRegion(region, outputPath, allowScreenCopyFallback);
        }

        return CaptureVirtualDesktop(outputPath, allowScreenCopyFallback);
    }

    private CaptureResult CaptureWindowRegion(WindowSummary window, WindowRect region, string? outputPath, bool allowScreenCopyFallback)
    {
        ValidateRegion(region);
        IntPtr handle = new(window.Handle);
        string resolvedPath = ResolveOutputPath(outputPath, $"frame-{window.HandleHex}-{region.Left}-{region.Top}-{region.Width}x{region.Height}.png");
        using var bitmap = new Bitmap(region.Width, region.Height, PixelFormat.Format32bppArgb);
        TryCaptureClientRegion(handle, bitmap, region, allowScreenCopyFallback);

        bitmap.Save(resolvedPath, ImageFormat.Png);
        return new CaptureResult(resolvedPath, "copyFromScreen", TranslateClientRegionToScreenBounds(handle, region));
    }

    private CaptureResult CaptureVirtualDesktop(string? outputPath, bool allowScreenCopyFallback)
    {
        int left = NativeMethods.GetSystemMetrics(NativeMethods.SmXVirtualScreen);
        int top = NativeMethods.GetSystemMetrics(NativeMethods.SmYVirtualScreen);
        int width = NativeMethods.GetSystemMetrics(NativeMethods.SmCxVirtualScreen);
        int height = NativeMethods.GetSystemMetrics(NativeMethods.SmCyVirtualScreen);
        if (width <= 0 || height <= 0)
        {
            throw new InvalidOperationException("The virtual desktop bounds could not be resolved.");
        }

        string resolvedPath = ResolveOutputPath(outputPath, $"virtual-desktop-{left}-{top}-{width}x{height}.png");
        using var bitmap = new Bitmap(width, height, PixelFormat.Format32bppArgb);
        CopyFromScreen(bitmap, new Rectangle(left, top, width, height), allowScreenCopyFallback);
        bitmap.Save(resolvedPath, ImageFormat.Png);
        return new CaptureResult(resolvedPath, "copyFromScreen", new WindowRect(left, top, width, height));
    }

    private static void TryCaptureClientRegion(IntPtr handle, Bitmap bitmap, WindowRect region, bool allowScreenCopyFallback)
    {
        NativeMethods.POINT topLeft = new() { X = region.Left, Y = region.Top };
        if (!NativeMethods.ClientToScreen(handle, ref topLeft))
        {
            throw new InvalidOperationException("Failed to translate client coordinates to screen coordinates.");
        }

        CopyFromScreen(bitmap, new Rectangle(topLeft.X, topLeft.Y, region.Width, region.Height), allowScreenCopyFallback);
    }

    private static void CopyFromScreen(Bitmap bitmap, Rectangle sourceBounds, bool allowScreenCopyFallback = true)
    {
        try
        {
            using Graphics graphics = Graphics.FromImage(bitmap);
            graphics.CopyFromScreen(sourceBounds.Left, sourceBounds.Top, 0, 0, bitmap.Size);
        }
        catch when (!allowScreenCopyFallback)
        {
            throw;
        }
    }

    private static bool TryPrintWindow(IntPtr handle, Bitmap bitmap)
    {
        using Graphics graphics = Graphics.FromImage(bitmap);
        IntPtr hdc = graphics.GetHdc();
        try
        {
            return NativeMethods.PrintWindow(handle, hdc, NativeMethods.PwRenderFullContent)
                || NativeMethods.PrintWindow(handle, hdc, 0);
        }
        finally
        {
            graphics.ReleaseHdc(hdc);
        }
    }

    private static WindowRect TranslateClientRegionToScreenBounds(IntPtr handle, WindowRect region)
    {
        NativeMethods.POINT topLeft = new() { X = region.Left, Y = region.Top };
        if (!NativeMethods.ClientToScreen(handle, ref topLeft))
        {
            throw new InvalidOperationException("Failed to translate client coordinates to screen coordinates.");
        }

        return new WindowRect(topLeft.X, topLeft.Y, region.Width, region.Height);
    }

    private static void ValidateRegion(WindowRect region)
    {
        if (region.Width <= 0 || region.Height <= 0)
        {
            throw new InvalidOperationException("The capture region must have positive width and height.");
        }
    }

    private static string BuildWindowFileName(WindowSummary window)
    {
        string safeTitle = string.Concat(window.Title.Select(character => Path.GetInvalidFileNameChars().Contains(character) ? '_' : character));
        return string.IsNullOrWhiteSpace(safeTitle)
            ? $"window-{window.HandleHex}.png"
            : $"{safeTitle}-{window.HandleHex}.png";
    }

    private static string ResolveOutputPath(string? outputPath, string defaultFileName)
    {
        string resolvedPath = outputPath ?? Path.Combine(Path.GetTempPath(), "agent-task-manager-automation", defaultFileName);
        string? directory = Path.GetDirectoryName(resolvedPath);
        if (!string.IsNullOrWhiteSpace(directory))
        {
            Directory.CreateDirectory(directory);
        }

        return resolvedPath;
    }
}
