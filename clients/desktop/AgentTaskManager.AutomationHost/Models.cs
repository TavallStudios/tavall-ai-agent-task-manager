using System.Text.Json;

namespace AgentTaskManager.AutomationHost;

internal sealed record AutomationRequest(string? Id, string Command, JsonElement Parameters);

internal sealed record AutomationResponse(string? Id, bool Ok, object? Result = null, AutomationError? Error = null);

internal sealed record AutomationError(string Code, string Message);

internal sealed record ListWindowsParameters(bool IncludeInvisible = false, string? TitleContains = null, string? ProcessName = null);

internal sealed record WindowTarget(long? Handle = null, string? TitleContains = null, string? ProcessName = null);

internal sealed record WindowRequest(WindowTarget Window);

internal sealed record WaitForWindowParameters(
    WindowTarget Window,
    int TimeoutMs = 10000,
    int PollIntervalMs = 250,
    bool IncludeInvisible = false);

internal sealed record DumpUiTreeParameters(
    WindowTarget Window,
    int MaxDepth = 5,
    int MaxChildrenPerNode = 25);

internal sealed record CaptureWindowParameters(
    WindowTarget Window,
    string? OutputPath = null,
    bool AllowScreenCopyFallback = true);

internal sealed record CaptureRegionParameters(
    int Left,
    int Top,
    int Width,
    int Height,
    string? OutputPath = null,
    bool AllowScreenCopyFallback = true);

internal sealed record CaptureStreamFrameParameters(
    WindowTarget? Window = null,
    WindowRect? Region = null,
    string? OutputPath = null,
    bool AllowScreenCopyFallback = true,
    bool IncludeBase64 = false);

internal sealed record ElementSelector(
    string? AutomationId = null,
    string? Name = null,
    string? NameContains = null,
    string? ControlType = null,
    string? ClassName = null,
    int Index = 0);

internal sealed record FindElementsParameters(
    WindowTarget Window,
    ElementSelector Selector,
    int MaxDepth = 8,
    int MaxResults = 25);

internal sealed record ElementCommandParameters(WindowTarget Window, ElementSelector Selector);

internal sealed record SetValueParameters(WindowTarget Window, ElementSelector Selector, string Value);

internal sealed record FocusWindowParameters(WindowTarget Window, bool RestoreIfMinimized = true);

internal sealed record MoveWindowParameters(
    WindowTarget Window,
    int Left,
    int Top,
    int Width,
    int Height,
    bool RestoreIfMinimized = true,
    bool ActivateWindow = false);

internal sealed record SendTextParameters(WindowTarget Window, string Text, bool ActivateWindow = false);

internal sealed record ClickPointParameters(
    WindowTarget Window,
    int X,
    int Y,
    string Mode = "windowMessage",
    bool ActivateWindow = false);

internal sealed record KeyBatchParameters(
    IReadOnlyList<KeyBatchEvent> Events,
    WindowTarget? Window = null,
    bool ActivateWindow = false);

internal sealed record KeyBatchEvent(
    string? Key = null,
    int? VirtualKey = null,
    int? ScanCode = null,
    string Action = "press",
    int DelayMs = 0,
    bool Extended = false);

internal sealed record MouseBatchParameters(
    IReadOnlyList<MouseBatchEvent> Events,
    WindowTarget? Window = null,
    bool ActivateWindow = false);

internal sealed record MouseBatchEvent(
    string Action,
    int X = 0,
    int Y = 0,
    string Button = "left",
    int WheelDelta = 0,
    int DelayMs = 0,
    string Coordinates = "screen");

internal sealed record LaunchProcessParameters(
    string FileName,
    string? Arguments = null,
    string? WorkingDirectory = null,
    bool WaitForInputIdle = false,
    int WaitForWindowMs = 0,
    string? WindowTitleContains = null);

internal sealed record MatchTemplateParameters(
    string TemplatePath,
    WindowTarget? Window = null,
    WindowRect? Region = null,
    double Threshold = 0.9,
    string? OutputPath = null,
    bool AllowScreenCopyFallback = true,
    bool IncludeBase64 = false);

internal sealed record WindowRect(int Left, int Top, int Width, int Height);

internal sealed record WindowSummary(
    long Handle,
    string HandleHex,
    string Title,
    string ClassName,
    int ProcessId,
    string ProcessName,
    bool IsVisible,
    bool IsMinimized,
    WindowRect Bounds);

internal sealed record UiElementSummary(
    string Path,
    string AutomationId,
    string Name,
    string ControlType,
    string ClassName,
    string FrameworkId,
    bool IsEnabled,
    bool IsOffscreen,
    WindowRect Bounds,
    string[] SupportedPatterns);

internal sealed record UiTreeNode(
    string Path,
    string AutomationId,
    string Name,
    string ControlType,
    string ClassName,
    string FrameworkId,
    bool IsEnabled,
    bool IsOffscreen,
    WindowRect Bounds,
    string[] SupportedPatterns,
    IReadOnlyList<UiTreeNode> Children);

internal sealed record CaptureResult(
    string OutputPath,
    string CaptureMode,
    WindowRect Bounds);

internal sealed record TemplateMatchResult(
    string TemplatePath,
    WindowRect SearchBounds,
    WindowRect MatchBounds,
    double Score,
    bool IsMatch,
    string? OutputPath);
