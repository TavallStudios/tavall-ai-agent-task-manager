namespace AgentTaskManager.Desktop.Contracts;

public sealed record RuntimeConnectionDto(
    string RuntimeId,
    string ConnectionState,
    string TransportKind,
    string AuthMode,
    string PreferredModel,
    string EndpointDescription,
    string SchemaVersion,
    DateTimeOffset? LastHeartbeatAt,
    string? ThreadId,
    string? LastTurnId,
    string? LastDisconnectReason);

public sealed record RuntimeLeaseDto(
    string SessionId,
    string LeaseState,
    string OwnerDeviceId,
    string OwnerHostName,
    bool HandoffAllowed,
    DateTimeOffset? LeaseExpiresAt,
    bool RemotelyResumable);

public sealed record RuntimeConnectionTelemetryDto(
    string RuntimeId,
    string Headline,
    string Detail,
    DateTimeOffset? LastHeartbeatAt,
    string ConnectionState,
    string AuthMode);

public sealed record RuntimeLaunchEnvelopeDto(
    string SessionId,
    string RuntimeId,
    string RuntimeKind,
    string TransportKind,
    string CommandPath,
    IReadOnlyList<string> Arguments,
    Dictionary<string, string> Environment,
    string ListenUri,
    string WorkspaceRoot,
    string WorkingDirectory,
    string ProfileKey,
    string PreferredModel,
    IReadOnlyList<string> RequiredMcpServers,
    IReadOnlyList<ProjectConfigLayerDto> ConfigLayers,
    string ApprovalPolicy,
    string SandboxMode,
    string SessionRuntimeDirectory,
    bool RequiresOwnership,
    bool LaunchReady,
    string Notes);

public sealed record RuntimeConnectedRequestDto(
    string RuntimeId,
    string ConnectionState,
    string TransportKind,
    string AuthMode,
    string PreferredModel,
    string EndpointDescription,
    string SchemaVersion,
    DateTimeOffset LastHeartbeatAt);

public sealed record RuntimeDisconnectedRequestDto(
    string RuntimeId,
    string ConnectionState,
    string DisconnectReason,
    bool Recoverable,
    DateTimeOffset ObservedAt);

public sealed record RuntimeEventPublishRequestDto(
    string RuntimeId,
    long SequenceNumber,
    string EventType,
    string? ThreadId,
    string? TurnId,
    DateTimeOffset OccurredAt,
    string Summary,
    Dictionary<string, object?> Attributes,
    string? RawNotificationName);
