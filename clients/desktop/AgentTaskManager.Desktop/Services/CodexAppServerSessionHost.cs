using AgentTaskManager.Desktop.Contracts;
using System.Diagnostics;
using System.Text.Json;

namespace AgentTaskManager.Desktop.Services;

internal sealed class CodexAppServerSessionHost : IAsyncDisposable
{
    private readonly RuntimeLaunchEnvelopeDto _launchEnvelope;
    private readonly IRuntimeSessionClientService _runtimeSessionClientService;
    private readonly SemaphoreSlim _gate = new(1, 1);
    private readonly TaskCompletionSource<string> _threadStarted = new(TaskCreationOptions.RunContinuationsAsynchronously);
    private Process? _process;
    private CodexJsonRpcWebSocketClient? _rpcClient;
    private long _sequenceNumber;
    private int _disconnectNotificationSent;
    private string? _threadId;
    private string? _backendTurnId;

    public CodexAppServerSessionHost(
        RuntimeLaunchEnvelopeDto launchEnvelope,
        IRuntimeSessionClientService runtimeSessionClientService)
    {
        _launchEnvelope = launchEnvelope;
        _runtimeSessionClientService = runtimeSessionClientService;
    }

    public string RuntimeId => _launchEnvelope.RuntimeId;

    public async Task EnsureConnectedAsync(SessionDetailDto session, CancellationToken cancellationToken)
    {
        await _gate.WaitAsync(cancellationToken);
        try
        {
            if (_rpcClient is { State: System.Net.WebSockets.WebSocketState.Open })
            {
                return;
            }

            StartProcess();
            string authMode = ResolveRuntimeAuthMode();
            _rpcClient = await ConnectWebSocketAsync(cancellationToken);
            _rpcClient.MessageReceived += HandleMessageAsync;
            await _rpcClient.SendRequestAsync(
                "initialize",
                new CodexInitializeParamsDto(new CodexClientInfoDto("AgentTaskManager.Desktop", "0.1.0"), new Dictionary<string, object?>()),
                cancellationToken);
            await _rpcClient.SendNotificationAsync<object?>("initialized", payload: null, cancellationToken);
            await _runtimeSessionClientService.MarkConnectedAsync(
                _launchEnvelope.SessionId,
                new RuntimeConnectedRequestDto(
                    _launchEnvelope.RuntimeId,
                    "CONNECTED",
                    _launchEnvelope.TransportKind,
                    authMode,
                    _launchEnvelope.PreferredModel,
                    _launchEnvelope.ListenUri,
                    "codex-app-server/v2",
                    DateTimeOffset.UtcNow),
                cancellationToken);
        }
        finally
        {
            _gate.Release();
        }
    }

    public async Task SendTurnAsync(
        SessionDetailDto session,
        string promptText,
        string? backendTurnId,
        CancellationToken cancellationToken)
    {
        await EnsureConnectedAsync(session, cancellationToken);
        _backendTurnId = backendTurnId;
        await EnsureThreadAsync(session, cancellationToken);
        await _rpcClient!.SendRequestAsync(
            "turn/start",
            BuildTurnStart(session, promptText),
            cancellationToken);
    }

    private async Task EnsureThreadAsync(SessionDetailDto session, CancellationToken cancellationToken)
    {
        if (!string.IsNullOrWhiteSpace(_threadId))
        {
            return;
        }

        string? resumedThreadId = string.IsNullOrWhiteSpace(session.RuntimeConnection.ThreadId)
            ? null
            : session.RuntimeConnection.ThreadId;
        if (!string.IsNullOrWhiteSpace(resumedThreadId))
        {
            try
            {
                await _rpcClient!.SendRequestAsync(
                    "thread/resume",
                    BuildThreadResumePayload(session, resumedThreadId),
                    cancellationToken);
                _threadId = resumedThreadId;
                return;
            }
            catch
            {
                // Fall back to starting a fresh app-server thread if the local runtime cannot resume.
            }
        }

        await _rpcClient!.SendRequestAsync("thread/start", BuildThreadStart(session), cancellationToken);
        _threadId = await _threadStarted.Task.WaitAsync(TimeSpan.FromSeconds(15), cancellationToken);
    }

    private async Task HandleMessageAsync(CodexAppServerMessageDto message)
    {
        if (string.IsNullOrWhiteSpace(message.Method))
        {
            return;
        }

        JsonElement? parameters = message.Params;
        Dictionary<string, object?> attributes = parameters.HasValue
            ? ConvertToDictionary(parameters.Value)
            : new Dictionary<string, object?>();

        string? runtimeThreadId = ReadThreadId(parameters);
        if (!string.IsNullOrWhiteSpace(runtimeThreadId))
        {
            _threadId = runtimeThreadId;
            _threadStarted.TrySetResult(runtimeThreadId);
            attributes["threadId"] = runtimeThreadId;
        }

        string? runtimeTurnId = ReadTurnId(parameters);
        if (!string.IsNullOrWhiteSpace(runtimeTurnId))
        {
            attributes["runtimeTurnId"] = runtimeTurnId;
        }

        string eventType = MapEventType(message.Method, parameters);
        string summary = BuildSummary(message.Method, parameters);
        await _runtimeSessionClientService.PublishEventAsync(
            _launchEnvelope.SessionId,
            new RuntimeEventPublishRequestDto(
                _launchEnvelope.RuntimeId,
                Interlocked.Increment(ref _sequenceNumber),
                eventType,
                _threadId,
                _backendTurnId,
                DateTimeOffset.UtcNow,
                summary,
                attributes,
                message.Method),
            CancellationToken.None);

        if (message.Method is "turn/completed" or "error")
        {
            _backendTurnId = null;
        }
    }

    private void StartProcess()
    {
        if (_process is { HasExited: false })
        {
            return;
        }

        ProcessStartInfo startInfo = CodexProcessStartInfoFactory.Build(
            _launchEnvelope.CommandPath,
            _launchEnvelope.WorkingDirectory,
            _launchEnvelope.Arguments,
            _launchEnvelope.Environment,
            createNoWindow: true,
            redirectOutput: false);
        _process = Process.Start(startInfo)
            ?? throw new InvalidOperationException($"Unable to start Codex app-server from '{startInfo.FileName}'.");
        _process.EnableRaisingEvents = true;
        _process.Exited += (_, _) => _ = NotifyProcessExitedAsync();
    }

    private async Task<CodexJsonRpcWebSocketClient> ConnectWebSocketAsync(CancellationToken cancellationToken)
    {
        Exception? lastError = null;
        for (int attempt = 0; attempt < 30; attempt++)
        {
            cancellationToken.ThrowIfCancellationRequested();
            if (_process?.HasExited == true)
            {
                throw new InvalidOperationException($"Codex app-server exited before accepting WebSocket connections. Exit code {_process.ExitCode}.");
            }

            var client = new CodexJsonRpcWebSocketClient(new Uri(_launchEnvelope.ListenUri));
            try
            {
                await client.ConnectAsync(cancellationToken);
                return client;
            }
            catch (Exception exception)
            {
                lastError = exception;
                await client.DisposeAsync();
                await Task.Delay(TimeSpan.FromMilliseconds(250), cancellationToken);
            }
        }

        throw new InvalidOperationException($"Unable to connect to Codex app-server at {_launchEnvelope.ListenUri}.", lastError);
    }

    private CodexThreadStartParamsDto BuildThreadStart(SessionDetailDto session)
        => new(
            NormalizeApprovalPolicy(session.WorkspaceBinding.ApprovalPolicy),
            "user",
            session.WorkspaceBinding.WorkingDirectory,
            new Dictionary<string, object?>(),
            BuildDeveloperInstructions(session),
            false,
            _launchEnvelope.PreferredModel,
            NormalizeSandbox(session.WorkspaceBinding.SandboxMode));

    private object BuildThreadResumePayload(SessionDetailDto session, string threadId)
        => new
        {
            threadId,
            approvalPolicy = NormalizeApprovalPolicy(session.WorkspaceBinding.ApprovalPolicy),
            approvalsReviewer = "user",
            cwd = session.WorkspaceBinding.WorkingDirectory,
            developerInstructions = BuildDeveloperInstructions(session),
            model = _launchEnvelope.PreferredModel,
            sandbox = NormalizeSandbox(session.WorkspaceBinding.SandboxMode)
        };

    private CodexTurnStartParamsDto BuildTurnStart(SessionDetailDto session, string promptText)
        => new(
            _threadId ?? throw new InvalidOperationException("Thread id was not established before turn dispatch."),
            new[] { new CodexUserInputDto("text", promptText) },
            NormalizeApprovalPolicy(session.WorkspaceBinding.ApprovalPolicy),
            "user",
            session.WorkspaceBinding.WorkingDirectory,
            _launchEnvelope.PreferredModel);

    private static string BuildDeveloperInstructions(SessionDetailDto session)
        => $"You are running inside AgentTaskManager Desktop. Stay inside '{session.WorkspaceBinding.WorkspaceRoot}'. " +
           "Treat the backend as the source of truth for approvals, verifier results, receipts, and final output release.";

    private static string NormalizeApprovalPolicy(string value) => value.Trim().ToLowerInvariant();

    private static string NormalizeSandbox(string value) => value.Trim().ToLowerInvariant();

    private static string MapEventType(string method, JsonElement? parameters)
    {
        string? itemType = ReadItemType(parameters);
        return method switch
        {
            "thread/started" => SessionEventTypes.ThreadStarted,
            "turn/started" => SessionEventTypes.TurnStarted,
            "turn/diff/updated" => SessionEventTypes.PatchPublished,
            "item/fileChange/outputDelta" => SessionEventTypes.PatchPublished,
            "item/agentMessage/delta" => SessionEventTypes.TurnDeltaReceived,
            "item/completed" when IsCandidateOutputItemType(itemType) => SessionEventTypes.CandidateOutputProduced,
            "item/completed" when IsToolReceiptItemType(itemType) => SessionEventTypes.ToolReceiptPublished,
            "item/started" => SessionEventTypes.ToolCallRequested,
            "error" => SessionEventTypes.OutputBlocked,
            _ => SessionEventTypes.TurnDeltaReceived
        };
    }

    private static string BuildSummary(string method, JsonElement? parameters)
    {
        string? message = ReadString(parameters, "message");
        if (!string.IsNullOrWhiteSpace(message))
        {
            return message;
        }

        string? delta = ReadString(parameters, "delta");
        if (!string.IsNullOrWhiteSpace(delta))
        {
            return delta.Length > 180 ? delta[..180] : delta;
        }

        string? status = ReadString(parameters, "status");
        string? itemType = ReadItemType(parameters);
        return string.IsNullOrWhiteSpace(status)
            ? $"{method}{(string.IsNullOrWhiteSpace(itemType) ? string.Empty : $" [{itemType}]")}"
            : $"{method} [{status}]";
    }

    private static string? ReadItemType(JsonElement? parameters)
        => parameters.HasValue
            && parameters.Value.ValueKind == JsonValueKind.Object
            && parameters.Value.TryGetProperty("item", out JsonElement item)
            && item.ValueKind == JsonValueKind.Object
            && item.TryGetProperty("type", out JsonElement typeElement)
            && typeElement.ValueKind == JsonValueKind.String
            ? typeElement.GetString()
            : null;

    private static string? ReadThreadId(JsonElement? parameters)
        => ReadString(parameters, "threadId")
           ?? ReadNestedString(parameters, "thread", "id");

    private static string? ReadTurnId(JsonElement? parameters)
        => ReadString(parameters, "turnId")
           ?? ReadNestedString(parameters, "turn", "id");

    private static string? ReadString(JsonElement? parameters, string propertyName)
        => parameters.HasValue
            && parameters.Value.ValueKind == JsonValueKind.Object
            && parameters.Value.TryGetProperty(propertyName, out JsonElement value)
            && value.ValueKind == JsonValueKind.String
            ? value.GetString()
            : null;

    private static bool IsCandidateOutputItemType(string? itemType)
        => itemType is "agent_message" or "agentMessage";

    private static bool IsToolReceiptItemType(string? itemType)
        => itemType is "mcp_tool_call" or "mcpToolCall" or "command_execution" or "commandExecution";

    private static string? ReadNestedString(JsonElement? parameters, string objectPropertyName, string nestedPropertyName)
        => parameters.HasValue
            && parameters.Value.ValueKind == JsonValueKind.Object
            && parameters.Value.TryGetProperty(objectPropertyName, out JsonElement nested)
            && nested.ValueKind == JsonValueKind.Object
            && nested.TryGetProperty(nestedPropertyName, out JsonElement value)
            && value.ValueKind == JsonValueKind.String
            ? value.GetString()
            : null;

    private static Dictionary<string, object?> ConvertToDictionary(JsonElement element)
        => JsonSerializer.Deserialize<Dictionary<string, object?>>(element.GetRawText(), DesktopJson.Default)
           ?? new Dictionary<string, object?>();

    private string ResolveRuntimeAuthMode()
        => _launchEnvelope.Environment.TryGetValue("AGENTTASKMANAGER_CODEX_AUTH_MODE", out string? authMode)
            && !string.IsNullOrWhiteSpace(authMode)
            ? authMode
            : "unknown";

    public async Task StopAsync(string reason, bool recoverable, CancellationToken cancellationToken)
    {
        try
        {
            await PublishDisconnectedAsync(reason, recoverable, cancellationToken);
        }
        finally
        {
            await DisposeAsync();
        }
    }

    private Task NotifyProcessExitedAsync()
    {
        string reason = _process == null
            ? "Codex process exited."
            : $"Codex process exited with code {_process.ExitCode}.";
        return PublishDisconnectedAsync(reason, true, CancellationToken.None);
    }

    private async Task PublishDisconnectedAsync(string reason, bool recoverable, CancellationToken cancellationToken)
    {
        if (Interlocked.Exchange(ref _disconnectNotificationSent, 1) != 0)
        {
            return;
        }

        try
        {
            await _runtimeSessionClientService.MarkDisconnectedAsync(
                _launchEnvelope.SessionId,
                new RuntimeDisconnectedRequestDto(
                    _launchEnvelope.RuntimeId,
                    "DISCONNECTED",
                    reason,
                    recoverable,
                    DateTimeOffset.UtcNow),
                cancellationToken);
        }
        catch
        {
        }
    }

    public async ValueTask DisposeAsync()
    {
        if (_rpcClient != null)
        {
            await _rpcClient.DisposeAsync();
            _rpcClient = null;
        }

        if (_process is { HasExited: false })
        {
            _process.Kill(true);
            _process.WaitForExit(5000);
        }

        _process?.Dispose();
        _process = null;
        _gate.Dispose();
    }
}
