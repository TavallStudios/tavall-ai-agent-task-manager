using AgentTaskManager.Desktop.Contracts;
using System.Collections.Concurrent;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;

namespace AgentTaskManager.Desktop.Services;

internal sealed class CodexJsonRpcWebSocketClient : IAsyncDisposable
{
    private readonly Uri _endpoint;
    private readonly ClientWebSocket _socket = new();
    private readonly SemaphoreSlim _sendGate = new(1, 1);
    private readonly ConcurrentDictionary<string, TaskCompletionSource<JsonElement?>> _pending = new();
    private readonly CancellationTokenSource _disposeCancellationTokenSource = new();
    private Task? _receiveLoopTask;
    private int _nextId;

    public CodexJsonRpcWebSocketClient(Uri endpoint)
    {
        _endpoint = endpoint;
    }

    public event Func<CodexAppServerMessageDto, Task>? MessageReceived;

    public WebSocketState State => _socket.State;

    public async Task ConnectAsync(CancellationToken cancellationToken)
    {
        await _socket.ConnectAsync(_endpoint, cancellationToken);
        _receiveLoopTask = ReceiveLoopAsync(_disposeCancellationTokenSource.Token);
    }

    public async Task<JsonElement?> SendRequestAsync<TParams>(string method, TParams? payload, CancellationToken cancellationToken)
    {
        string id = Interlocked.Increment(ref _nextId).ToString();
        var pending = new TaskCompletionSource<JsonElement?>(TaskCreationOptions.RunContinuationsAsynchronously);
        _pending[id] = pending;

        try
        {
            await SendAsync(new CodexJsonRpcRequestDto("2.0", id, method, payload), cancellationToken);
            return await pending.Task.WaitAsync(cancellationToken);
        }
        finally
        {
            _pending.TryRemove(id, out _);
        }
    }

    public Task SendNotificationAsync<TParams>(string method, TParams? payload, CancellationToken cancellationToken)
        => SendAsync(new CodexJsonRpcNotificationDto("2.0", method, payload), cancellationToken);

    private async Task SendAsync(object payload, CancellationToken cancellationToken)
    {
        string json = JsonSerializer.Serialize(payload, DesktopJson.Default);
        byte[] buffer = Encoding.UTF8.GetBytes(json);
        await _sendGate.WaitAsync(cancellationToken);
        try
        {
            await _socket.SendAsync(buffer, WebSocketMessageType.Text, true, cancellationToken);
        }
        finally
        {
            _sendGate.Release();
        }
    }

    private async Task ReceiveLoopAsync(CancellationToken cancellationToken)
    {
        try
        {
            while (!cancellationToken.IsCancellationRequested && _socket.State == WebSocketState.Open)
            {
                string payload = await ReceiveTextMessageAsync(cancellationToken);
                if (string.IsNullOrWhiteSpace(payload))
                {
                    continue;
                }

                CodexAppServerMessageDto? message = JsonSerializer.Deserialize<CodexAppServerMessageDto>(payload, DesktopJson.Default);
                if (message == null)
                {
                    continue;
                }

                if (!string.IsNullOrWhiteSpace(message.Id) && message.Method == null)
                {
                    ResolvePending(message);
                    continue;
                }

                if (!string.IsNullOrWhiteSpace(message.Id) && message.Method != null)
                {
                    await SendUnsupportedServerRequestAsync(message.Id, message.Method, cancellationToken);
                }

                Func<CodexAppServerMessageDto, Task>? handler = MessageReceived;
                if (handler != null)
                {
                    await handler(message);
                }
            }
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
        }
        catch (Exception exception)
        {
            FailPending(exception);
            throw;
        }
        finally
        {
            FailPending(new IOException("Codex app-server WebSocket disconnected."));
        }
    }

    private async Task<string> ReceiveTextMessageAsync(CancellationToken cancellationToken)
    {
        var buffer = new byte[8192];
        using var stream = new MemoryStream();
        while (true)
        {
            WebSocketReceiveResult result = await _socket.ReceiveAsync(buffer, cancellationToken);
            if (result.MessageType == WebSocketMessageType.Close)
            {
                await _socket.CloseOutputAsync(WebSocketCloseStatus.NormalClosure, "closing", CancellationToken.None);
                return string.Empty;
            }

            stream.Write(buffer, 0, result.Count);
            if (result.EndOfMessage)
            {
                break;
            }
        }

        return Encoding.UTF8.GetString(stream.ToArray());
    }

    private void ResolvePending(CodexAppServerMessageDto message)
    {
        if (message.Id == null || !_pending.TryGetValue(message.Id, out TaskCompletionSource<JsonElement?>? pending))
        {
            return;
        }

        if (message.Error != null)
        {
            pending.TrySetException(new InvalidOperationException($"{message.Error.Code}: {message.Error.Message}"));
            return;
        }

        pending.TrySetResult(message.Result);
    }

    private async Task SendUnsupportedServerRequestAsync(string id, string method, CancellationToken cancellationToken)
    {
        var error = new
        {
            jsonrpc = "2.0",
            id,
            error = new
            {
                code = -32000,
                message = $"Server-initiated request '{method}' is not supported by the desktop scaffold."
            }
        };
        await SendAsync(error, cancellationToken);
    }

    private void FailPending(Exception exception)
    {
        foreach ((_, TaskCompletionSource<JsonElement?> pending) in _pending)
        {
            pending.TrySetException(exception);
        }
        _pending.Clear();
    }

    public async ValueTask DisposeAsync()
    {
        _disposeCancellationTokenSource.Cancel();
        if (_socket.State is WebSocketState.Open or WebSocketState.CloseReceived)
        {
            await _socket.CloseAsync(WebSocketCloseStatus.NormalClosure, "dispose", CancellationToken.None);
        }

        if (_receiveLoopTask != null)
        {
            try
            {
                await _receiveLoopTask;
            }
            catch
            {
            }
        }

        _socket.Dispose();
        _sendGate.Dispose();
        _disposeCancellationTokenSource.Dispose();
    }
}
