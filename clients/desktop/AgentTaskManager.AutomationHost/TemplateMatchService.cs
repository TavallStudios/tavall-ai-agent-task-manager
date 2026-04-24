using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;

namespace AgentTaskManager.AutomationHost;

internal sealed class TemplateMatchService
{
    private readonly WindowCaptureService _captureService = new();
    private readonly WindowQueryService _windowQueryService = new();

    internal TemplateMatchResult Match(MatchTemplateParameters request)
    {
        if (request.Threshold is < 0 or > 1)
        {
            throw new InvalidOperationException("The match threshold must be between 0 and 1.");
        }

        string sourcePath = Path.Combine(Path.GetTempPath(), "tavall-ai-automation", $"template-source-{Guid.NewGuid():N}.png");
        WindowSummary? resolvedWindow = request.Window is null
            ? null
            : _windowQueryService.ResolveWindow(request.Window, includeInvisible: true);
        CaptureResult capture = _captureService.CaptureStreamFrame(resolvedWindow, request.Region, sourcePath, request.AllowScreenCopyFallback);
        using Bitmap source = new(capture.OutputPath);
        using Bitmap template = new(request.TemplatePath);

        MatchResult best = FindBestMatch(source, template, request.Threshold);
        string? outputPath = request.OutputPath;
        if (!string.IsNullOrWhiteSpace(outputPath))
        {
            WriteAnnotatedCapture(source, best.Bounds, outputPath);
        }

        TryDelete(sourcePath);
        return new TemplateMatchResult(
            TemplatePath: Path.GetFullPath(request.TemplatePath),
            SearchBounds: capture.Bounds,
            MatchBounds: best.Bounds,
            Score: best.Score,
            IsMatch: best.Score >= request.Threshold,
            OutputPath: outputPath);
    }

    private static MatchResult FindBestMatch(Bitmap source, Bitmap template, double threshold)
    {
        if (template.Width <= 0 || template.Height <= 0)
        {
            throw new InvalidOperationException("The template image is empty.");
        }

        if (source.Width < template.Width || source.Height < template.Height)
        {
            throw new InvalidOperationException("The template image is larger than the source image.");
        }

        byte[] sourceGray = ToGrayscale(source);
        byte[] templateGray = ToGrayscale(template);
        int bestX = 0;
        int bestY = 0;
        double bestScore = double.NegativeInfinity;
        int searchWidth = source.Width - template.Width + 1;
        int searchHeight = source.Height - template.Height + 1;
        for (int y = 0; y < searchHeight; y++)
        {
            for (int x = 0; x < searchWidth; x++)
            {
                double score = ScoreAt(sourceGray, source.Width, templateGray, template.Width, template.Height, x, y);
                if (score > bestScore)
                {
                    bestScore = score;
                    bestX = x;
                    bestY = y;
                    if (bestScore >= threshold)
                    {
                        // Keep scanning to find the strongest match, but this gives a fast early signal.
                    }
                }
            }
        }

        return new MatchResult(new WindowRect(bestX, bestY, template.Width, template.Height), bestScore);
    }

    private static double ScoreAt(byte[] sourceGray, int sourceWidth, byte[] templateGray, int templateWidth, int templateHeight, int offsetX, int offsetY)
    {
        long difference = 0;
        for (int y = 0; y < templateHeight; y++)
        {
            int sourceRow = (offsetY + y) * sourceWidth + offsetX;
            int templateRow = y * templateWidth;
            for (int x = 0; x < templateWidth; x++)
            {
                difference += Math.Abs(sourceGray[sourceRow + x] - templateGray[templateRow + x]);
            }
        }

        double maxDifference = templateWidth * templateHeight * 255d;
        return 1d - (difference / maxDifference);
    }

    private static byte[] ToGrayscale(Bitmap bitmap)
    {
        Rectangle bounds = new(0, 0, bitmap.Width, bitmap.Height);
        Bitmap working = bitmap.PixelFormat == PixelFormat.Format32bppArgb
            ? bitmap
            : bitmap.Clone(bounds, PixelFormat.Format32bppArgb);
        try
        {
            BitmapData data = working.LockBits(bounds, ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
            try
            {
                int bytes = Math.Abs(data.Stride) * data.Height;
                byte[] raw = new byte[bytes];
                Marshal.Copy(data.Scan0, raw, 0, bytes);
                byte[] grayscale = new byte[bitmap.Width * bitmap.Height];
                int index = 0;
                for (int y = 0; y < bitmap.Height; y++)
                {
                    int row = y * data.Stride;
                    for (int x = 0; x < bitmap.Width; x++)
                    {
                        int pixel = row + (x * 4);
                        byte b = raw[pixel];
                        byte g = raw[pixel + 1];
                        byte r = raw[pixel + 2];
                        grayscale[index++] = (byte)((r * 299 + g * 587 + b * 114) / 1000);
                    }
                }

                return grayscale;
            }
            finally
            {
                working.UnlockBits(data);
            }
        }
        finally
        {
            if (!ReferenceEquals(working, bitmap))
            {
                working.Dispose();
            }
        }
    }

    private static void WriteAnnotatedCapture(Bitmap source, WindowRect matchBounds, string outputPath)
    {
        string? directory = Path.GetDirectoryName(outputPath);
        if (!string.IsNullOrWhiteSpace(directory))
        {
            Directory.CreateDirectory(directory);
        }

        using Bitmap annotated = new(source);
        using Graphics graphics = Graphics.FromImage(annotated);
        using Pen pen = new(Color.Red, 3);
        graphics.DrawRectangle(pen, matchBounds.Left, matchBounds.Top, Math.Max(1, matchBounds.Width), Math.Max(1, matchBounds.Height));
        annotated.Save(outputPath, ImageFormat.Png);
    }

    private static void TryDelete(string path)
    {
        try
        {
            File.Delete(path);
        }
        catch
        {
        }
    }

    private sealed record MatchResult(WindowRect Bounds, double Score);
}

