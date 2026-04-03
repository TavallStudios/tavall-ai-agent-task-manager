using System.Text.Json;

namespace AgentTaskManager.Desktop.Contracts;

public sealed record CodexJsonRpcRequestDto(
    string Jsonrpc,
    string Id,
    string Method,
    object? Params);

public sealed record CodexJsonRpcNotificationDto(
    string Jsonrpc,
    string Method,
    object? Params);

public sealed record CodexJsonRpcSuccessResponseDto(
    string Jsonrpc,
    string Id,
    JsonElement Result);

public sealed record CodexJsonRpcErrorBodyDto(
    int Code,
    string Message,
    JsonElement? Data);

public sealed record CodexJsonRpcErrorResponseDto(
    string Jsonrpc,
    string Id,
    CodexJsonRpcErrorBodyDto Error);

public sealed record CodexInitializeParamsDto(
    CodexClientInfoDto ClientInfo,
    Dictionary<string, object?>? Capabilities);

public sealed record CodexClientInfoDto(
    string Name,
    string Version);

public sealed record CodexThreadStartParamsDto(
    string? ApprovalPolicy,
    string? ApprovalsReviewer,
    string? Cwd,
    Dictionary<string, object?>? Config,
    string? DeveloperInstructions,
    bool? Ephemeral,
    string? Model,
    string? Sandbox);

public sealed record CodexTurnStartParamsDto(
    string ThreadId,
    IReadOnlyList<CodexUserInputDto> Input,
    string? ApprovalPolicy,
    string? ApprovalsReviewer,
    string? Cwd,
    string? Model);

public sealed record CodexUserInputDto(
    string Type,
    string Text);

public sealed record CodexAppServerMessageDto(
    string? Id,
    string? Method,
    JsonElement? Params,
    JsonElement? Result,
    CodexJsonRpcErrorBodyDto? Error);
