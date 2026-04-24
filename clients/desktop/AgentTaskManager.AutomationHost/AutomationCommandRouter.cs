using System.Diagnostics;
using System.IO;
using System.Text.Json;

namespace AgentTaskManager.AutomationHost;

internal sealed class AutomationCommandRouter
{
    private static readonly string[] SupportedCommands =
    {
        "ping",
        "list_windows",
        "wait_for_window",
        "capture_window",
        "capture_region",
        "capture_stream_frame",
        "dump_ui_tree",
        "find_elements",
        "invoke_element",
        "set_value",
        "select_element",
        "focus_window",
        "move_window",
        "send_text",
        "click_point",
        "send_key_batch",
        "send_mouse_batch",
        "match_template",
        "launch_process"
    };

    private readonly JsonSerializerOptions _jsonOptions;
    private readonly WindowQueryService _windowQueryService = new();
    private readonly UiAutomationService _uiAutomationService = new();
    private readonly WindowCaptureService _windowCaptureService = new();
    private readonly InputInjectionService _inputInjectionService = new();
    private readonly WindowPlacementService _windowPlacementService = new();
    private readonly TemplateMatchService _templateMatchService = new();

    internal AutomationCommandRouter(JsonSerializerOptions jsonOptions)
    {
        _jsonOptions = jsonOptions;
    }

    internal async Task<AutomationResponse> ExecuteAsync(AutomationRequest request, CancellationToken cancellationToken)
    {
        try
        {
            object? result = request.Command switch
            {
                "ping" => new { status = "ok" },
                "list_windows" => HandleListWindows(request.Parameters),
                "wait_for_window" => await HandleWaitForWindowAsync(request.Parameters, cancellationToken),
                "capture_window" => HandleCaptureWindow(request.Parameters),
                "capture_region" => HandleCaptureRegion(request.Parameters),
                "capture_stream_frame" => HandleCaptureStreamFrame(request.Parameters),
                "dump_ui_tree" => HandleDumpUiTree(request.Parameters),
                "find_elements" => HandleFindElements(request.Parameters),
                "invoke_element" => HandleInvokeElement(request.Parameters),
                "set_value" => HandleSetValue(request.Parameters),
                "select_element" => HandleSelectElement(request.Parameters),
                "focus_window" => HandleFocusWindow(request.Parameters),
                "move_window" => HandleMoveWindow(request.Parameters),
                "send_text" => HandleSendText(request.Parameters),
                "click_point" => HandleClickPoint(request.Parameters),
                "send_key_batch" => await HandleSendKeyBatchAsync(request.Parameters, cancellationToken),
                "send_mouse_batch" => await HandleSendMouseBatchAsync(request.Parameters, cancellationToken),
                "match_template" => HandleMatchTemplate(request.Parameters),
                "launch_process" => await HandleLaunchProcessAsync(request.Parameters, cancellationToken),
                _ => throw new InvalidOperationException($"Unsupported command: {request.Command}")
            };
            return new AutomationResponse(request.Id, true, Result: result);
        }
        catch (Exception exception)
        {
            return new AutomationResponse(
                request.Id,
                false,
                Error: new AutomationError("command_failed", exception.Message));
        }
    }

    internal async Task<AutomationResponse> ExecuteJsonAsync(string json, CancellationToken cancellationToken)
    {
        try
        {
            string normalizedJson = json.TrimStart('\uFEFF', '\u200B', '\u0000').Trim();
            AutomationRequest request = JsonSerializer.Deserialize<AutomationRequest>(normalizedJson, _jsonOptions)
                ?? throw new InvalidOperationException("The request payload was empty.");
            return await ExecuteAsync(request, cancellationToken);
        }
        catch (Exception exception)
        {
            return new AutomationResponse(null, false, Error: new AutomationError("invalid_request", exception.Message));
        }
    }

    internal IReadOnlyList<string> ListSupportedCommands()
        => SupportedCommands;

    private IReadOnlyList<WindowSummary> HandleListWindows(JsonElement parameters)
    {
        ListWindowsParameters request = Deserialize<ListWindowsParameters>(parameters);
        return _windowQueryService.ListWindows(request.IncludeInvisible, request.TitleContains, request.ProcessName);
    }

    private async Task<WindowSummary> HandleWaitForWindowAsync(JsonElement parameters, CancellationToken cancellationToken)
    {
        WaitForWindowParameters request = Deserialize<WaitForWindowParameters>(parameters);
        Stopwatch stopwatch = Stopwatch.StartNew();
        while (stopwatch.ElapsedMilliseconds < request.TimeoutMs)
        {
            try
            {
                return _windowQueryService.ResolveWindow(request.Window, request.IncludeInvisible);
            }
            catch
            {
                await Task.Delay(request.PollIntervalMs, cancellationToken);
            }
        }

        throw new TimeoutException("Timed out waiting for a matching window.");
    }

    private CaptureResult HandleCaptureWindow(JsonElement parameters)
    {
        CaptureWindowParameters request = Deserialize<CaptureWindowParameters>(parameters);
        WindowSummary window = _windowQueryService.ResolveWindow(request.Window, includeInvisible: true);
        return _windowCaptureService.Capture(window, request.OutputPath, request.AllowScreenCopyFallback);
    }

    private CaptureResult HandleCaptureRegion(JsonElement parameters)
    {
        CaptureRegionParameters request = Deserialize<CaptureRegionParameters>(parameters);
        return _windowCaptureService.CaptureRegion(new WindowRect(request.Left, request.Top, request.Width, request.Height), request.OutputPath, request.AllowScreenCopyFallback);
    }

    private object HandleCaptureStreamFrame(JsonElement parameters)
    {
        CaptureStreamFrameParameters request = Deserialize<CaptureStreamFrameParameters>(parameters);
        WindowSummary? window = request.Window is null ? null : _windowQueryService.ResolveWindow(request.Window, includeInvisible: true);
        CaptureResult capture = _windowCaptureService.CaptureStreamFrame(window, request.Region, request.OutputPath, request.AllowScreenCopyFallback);
        return request.IncludeBase64 ? IncludeBase64(capture) : capture;
    }

    private UiTreeNode HandleDumpUiTree(JsonElement parameters)
    {
        DumpUiTreeParameters request = Deserialize<DumpUiTreeParameters>(parameters);
        WindowSummary window = _windowQueryService.ResolveWindow(request.Window, includeInvisible: true);
        return _uiAutomationService.DumpTree(window, request.MaxDepth, request.MaxChildrenPerNode);
    }

    private IReadOnlyList<UiElementSummary> HandleFindElements(JsonElement parameters)
    {
        FindElementsParameters request = Deserialize<FindElementsParameters>(parameters);
        WindowSummary window = _windowQueryService.ResolveWindow(request.Window, includeInvisible: true);
        return _uiAutomationService.FindElements(window, request.Selector, request.MaxDepth, request.MaxResults);
    }

    private UiElementSummary HandleInvokeElement(JsonElement parameters)
    {
        ElementCommandParameters request = Deserialize<ElementCommandParameters>(parameters);
        WindowSummary window = _windowQueryService.ResolveWindow(request.Window, includeInvisible: true);
        return _uiAutomationService.Invoke(window, request.Selector);
    }

    private UiElementSummary HandleSetValue(JsonElement parameters)
    {
        SetValueParameters request = Deserialize<SetValueParameters>(parameters);
        WindowSummary window = _windowQueryService.ResolveWindow(request.Window, includeInvisible: true);
        return _uiAutomationService.SetValue(window, request.Selector, request.Value);
    }

    private UiElementSummary HandleSelectElement(JsonElement parameters)
    {
        ElementCommandParameters request = Deserialize<ElementCommandParameters>(parameters);
        WindowSummary window = _windowQueryService.ResolveWindow(request.Window, includeInvisible: true);
        return _uiAutomationService.Select(window, request.Selector);
    }

    private object HandleFocusWindow(JsonElement parameters)
    {
        FocusWindowParameters request = Deserialize<FocusWindowParameters>(parameters);
        WindowSummary window = _windowQueryService.ResolveWindow(request.Window, includeInvisible: true);
        return _inputInjectionService.Focus(window, request.RestoreIfMinimized);
    }

    private object HandleMoveWindow(JsonElement parameters)
    {
        MoveWindowParameters request = Deserialize<MoveWindowParameters>(parameters);
        WindowSummary window = _windowQueryService.ResolveWindow(request.Window, includeInvisible: true);
        return _windowPlacementService.Move(
            window,
            request.Left,
            request.Top,
            request.Width,
            request.Height,
            request.RestoreIfMinimized,
            request.ActivateWindow);
    }

    private object HandleSendText(JsonElement parameters)
    {
        SendTextParameters request = Deserialize<SendTextParameters>(parameters);
        WindowSummary window = _windowQueryService.ResolveWindow(request.Window, includeInvisible: true);
        return _inputInjectionService.SendText(window, request.Text, request.ActivateWindow);
    }

    private object HandleClickPoint(JsonElement parameters)
    {
        ClickPointParameters request = Deserialize<ClickPointParameters>(parameters);
        WindowSummary window = _windowQueryService.ResolveWindow(request.Window, includeInvisible: true);
        return _inputInjectionService.Click(window, request.X, request.Y, request.Mode, request.ActivateWindow);
    }

    private async Task<object> HandleSendKeyBatchAsync(JsonElement parameters, CancellationToken cancellationToken)
    {
        KeyBatchParameters request = Deserialize<KeyBatchParameters>(parameters);
        WindowSummary? window = request.Window is null ? null : _windowQueryService.ResolveWindow(request.Window, includeInvisible: true);
        return await _inputInjectionService.SendKeyBatchAsync(window, request.Events, request.ActivateWindow, cancellationToken);
    }

    private async Task<object> HandleSendMouseBatchAsync(JsonElement parameters, CancellationToken cancellationToken)
    {
        MouseBatchParameters request = Deserialize<MouseBatchParameters>(parameters);
        WindowSummary? window = request.Window is null ? null : _windowQueryService.ResolveWindow(request.Window, includeInvisible: true);
        return await _inputInjectionService.SendMouseBatchAsync(window, request.Events, request.ActivateWindow, cancellationToken);
    }

    private object HandleMatchTemplate(JsonElement parameters)
    {
        MatchTemplateParameters request = Deserialize<MatchTemplateParameters>(parameters);
        TemplateMatchResult result = _templateMatchService.Match(request);
        return MapTemplateMatch(result, request.IncludeBase64);
    }

    private async Task<object> HandleLaunchProcessAsync(JsonElement parameters, CancellationToken cancellationToken)
    {
        LaunchProcessParameters request = Deserialize<LaunchProcessParameters>(parameters);
        var startInfo = new ProcessStartInfo
        {
            FileName = request.FileName,
            Arguments = request.Arguments ?? string.Empty,
            UseShellExecute = true
        };
        if (!string.IsNullOrWhiteSpace(request.WorkingDirectory))
        {
            startInfo.WorkingDirectory = request.WorkingDirectory;
        }

        Process? process = Process.Start(startInfo);
        if (process is null)
        {
            throw new InvalidOperationException("Failed to launch the requested process.");
        }

        if (request.WaitForInputIdle)
        {
            try
            {
                process.WaitForInputIdle();
            }
            catch
            {
            }
        }

        WindowSummary? window = null;
        if (request.WaitForWindowMs > 0)
        {
            WaitForWindowParameters waitParameters = new(
                new WindowTarget(TitleContains: request.WindowTitleContains, ProcessName: process.ProcessName),
                TimeoutMs: request.WaitForWindowMs);
            window = await HandleWaitForWindowAsync(JsonSerializer.SerializeToElement(waitParameters, _jsonOptions), cancellationToken);
        }

        return new
        {
            process.Id,
            process.ProcessName,
            window
        };
    }

    private T Deserialize<T>(JsonElement parameters)
        where T : class
        => parameters.ValueKind == JsonValueKind.Undefined || parameters.ValueKind == JsonValueKind.Null
            ? throw new InvalidOperationException("The command parameters were missing.")
            : JsonSerializer.Deserialize<T>(parameters.GetRawText(), _jsonOptions)
                ?? throw new InvalidOperationException("The command parameters could not be parsed.");

    private static object IncludeBase64(CaptureResult capture)
        => new
        {
            capture.OutputPath,
            capture.CaptureMode,
            capture.Bounds,
            base64Png = ReadBase64(capture.OutputPath)
        };

    private static object MapTemplateMatch(TemplateMatchResult result, bool includeBase64)
        => new
        {
            result.TemplatePath,
            searchBounds = result.SearchBounds,
            bounds = result.MatchBounds,
            result.Score,
            matched = result.IsMatch,
            result.OutputPath,
            base64Png = includeBase64 && !string.IsNullOrWhiteSpace(result.OutputPath) ? ReadBase64(result.OutputPath) : string.Empty
        };

    private static string ReadBase64(string outputPath)
        => Convert.ToBase64String(File.ReadAllBytes(outputPath));
}
