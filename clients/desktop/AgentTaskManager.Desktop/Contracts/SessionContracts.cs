namespace AgentTaskManager.Desktop.Contracts;

public sealed record WorkspaceDescriptorDto(
    string DisplayName,
    string WorkspaceRoot,
    string RepoPath,
    bool IsGitRepository,
    bool HasProjectCodexConfig,
    bool HasMcpOverrides,
    string DefaultProfileKey,
    DateTimeOffset? LastUsedAt)
{
    public string DisplaySummary => $"{DisplayName}  [{WorkspaceRoot}]";
}

public sealed record ProjectConfigLayerDto(
    string LayerName,
    string SourcePath,
    bool ExistsOnDisk,
    bool Active,
    bool Trusted);

public sealed record ResolvedMcpServerDto(
    string Name,
    string Source,
    string TransportKind,
    bool Required,
    string Value);

public sealed record SessionSummaryDto(
    string SessionId,
    string Title,
    string ProjectKey,
    string RepoPath,
    string WorkspaceRoot,
    string ClientSurface,
    string LifecycleState,
    string RuntimeConnectionState,
    string OutputReleaseState,
    bool RemotelyResumable,
    DateTimeOffset CreatedAt,
    DateTimeOffset LastEventAt,
    string? ActiveTurnId,
    string RuntimeId)
{
    public string DisplaySummary
        => $"{Title}  [{LifecycleState}]  {RuntimeConnectionState}  {LastEventAt.ToLocalTime():MMM d HH:mm}";
}

public sealed record WorkspaceBindingDto(
    string SessionId,
    string ProjectKey,
    string RepoPath,
    string WorkspaceRoot,
    string WorkspaceScope,
    string WorkingDirectory,
    string ProfileKey,
    IReadOnlyList<ProjectConfigLayerDto> ConfigLayers,
    IReadOnlyList<ResolvedMcpServerDto> McpServers,
    string ApprovalPolicy,
    string SandboxMode,
    bool UtilitySession);

public sealed record DevicePresenceDto(
    string DeviceId,
    string DeviceName,
    string ClientSurface,
    string HostName,
    string PresenceState,
    bool RuntimeOwner,
    DateTimeOffset LastSeenAt)
{
    public string DisplaySummary
        => $"{DeviceName}  {PresenceState}  {(RuntimeOwner ? "runtime owner" : "observer")}  {HostName}";
}

public sealed record TurnSummaryDto(
    string TurnId,
    string Status,
    string RequestedMode,
    string RequestedBy,
    DateTimeOffset CreatedAt,
    DateTimeOffset LastUpdatedAt,
    bool AwaitingVerifier,
    bool ApprovedOutputAvailable)
{
    public string DisplaySummary
        => $"{RequestedMode}  {Status}  {RequestedBy}  {LastUpdatedAt.ToLocalTime():HH:mm:ss}";
}

public sealed record ToolReceiptDto(
    string ReceiptId,
    string TurnId,
    string ToolName,
    string ReceiptKind,
    string Status,
    string Summary,
    DateTimeOffset RecordedAt)
{
    public string DisplaySummary
        => $"{ToolName}  {Status}  {ReceiptKind}  {Summary}";
}

public sealed record VerifierResultDto(
    string VerifierId,
    string TurnId,
    string Status,
    bool Blocking,
    string Summary,
    string EvidenceUri,
    DateTimeOffset RecordedAt)
{
    public string DisplaySummary
        => $"{Status}  {(Blocking ? "blocking" : "advisory")}  {Summary}";
}

public sealed record OutputSnapshotDto(
    string OutputId,
    string TurnId,
    bool Approved,
    string Summary,
    string Content,
    string ReleaseState,
    DateTimeOffset RecordedAt);

public sealed record PatchFileChangeDto(
    string Path,
    string ChangeType,
    int AddedLines,
    int RemovedLines);

public sealed record PatchArtifactDto(
    string PatchId,
    string TurnId,
    string RepoPath,
    string? BaseRevision,
    string? HeadRevision,
    string Summary,
    string DiffPreview,
    string? ArtifactBodyReference,
    IReadOnlyList<PatchFileChangeDto> ChangedFiles,
    DateTimeOffset RecordedAt)
{
    public string DisplaySummary
        => $"{Summary}  {ChangedFiles.Count} file(s)  {RecordedAt.ToLocalTime():HH:mm:ss}";
}

public sealed record FileFocusRequestDto(
    string RequestId,
    string TurnId,
    string Path,
    int? Line,
    int? Column,
    string Reason,
    string? LaunchHint,
    DateTimeOffset CreatedAt)
{
    public string DisplaySummary
        => $"{Path}{FormatLocation(Line, Column)}  {Reason}";

    private static string FormatLocation(int? line, int? column)
        => line is null
            ? string.Empty
            : column is null
                ? $":{line}"
                : $":{line}:{column}";
}

public sealed record MemoryContextReferenceDto(
    string ReferenceId,
    string? TurnId,
    string MemoryKind,
    string SourceType,
    string Summary,
    string? BodyPreview,
    DateTimeOffset RecordedAt)
{
    public string DisplaySummary => $"{MemoryKind}  {SourceType}  {Summary}";
}

public sealed record SessionEventEnvelopeDto(
    string EventId,
    string SessionId,
    string? TurnId,
    string EventType,
    string SchemaVersion,
    string Source,
    DateTimeOffset OccurredAt,
    Dictionary<string, object?> Attributes,
    string Summary)
{
    public string DisplaySummary
        => $"{OccurredAt.ToLocalTime():HH:mm:ss}  {EventType}  {Summary}";
}

public sealed record SessionDetailDto(
    SessionSummaryDto Summary,
    WorkspaceBindingDto WorkspaceBinding,
    RuntimeConnectionDto RuntimeConnection,
    RuntimeLeaseDto RuntimeLease,
    IReadOnlyList<DevicePresenceDto> Devices,
    IReadOnlyList<TurnSummaryDto> Turns,
    IReadOnlyList<ToolReceiptDto> ToolReceipts,
    IReadOnlyList<VerifierResultDto> VerifierResults,
    IReadOnlyList<OutputSnapshotDto> Outputs,
    IReadOnlyList<PatchArtifactDto> Patches,
    IReadOnlyList<FileFocusRequestDto> FileFocusRequests,
    IReadOnlyList<MemoryContextReferenceDto> MemoryReferences,
    IReadOnlyList<SessionEventEnvelopeDto> RecentEvents);

public sealed record CreateSessionRequestDto(
    string Title,
    string ProjectKey,
    string RepoPath,
    string WorkspaceRoot,
    string ProfileKey,
    string ClientSurface,
    string WorkspaceScope,
    bool UtilitySession,
    bool CreateRuntime,
    string InitialPrompt);

public sealed record AttachSessionRequestDto(
    string DeviceId,
    string DeviceName,
    string ClientSurface,
    string HostName,
    bool ObserveOnly);

public sealed record ResumeSessionRequestDto(
    string DeviceId,
    string HostName,
    bool RequestOwnership,
    bool AllowRuntimeHandoff);

public sealed record SubmitTurnRequestDto(
    string PromptText,
    string RequestedMode,
    IReadOnlyList<string> RequiredReceiptKinds,
    bool AllowFileEdits);

public sealed record SessionListResponseDto(IReadOnlyList<SessionSummaryDto> Items);

public sealed record EventListResponseDto(IReadOnlyList<SessionEventEnvelopeDto> Items);
