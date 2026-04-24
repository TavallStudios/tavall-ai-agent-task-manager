namespace AgentTaskManager.Desktop.Contracts;

public static class SessionEventTypes
{
    public const string SessionCreated = "SessionCreated";
    public const string SessionAttached = "SessionAttached";
    public const string SessionResumed = "SessionResumed";
    public const string WorkspaceBound = "WorkspaceBound";
    public const string ThreadStarted = "ThreadStarted";
    public const string TurnStarted = "TurnStarted";
    public const string TurnDeltaReceived = "TurnDeltaReceived";
    public const string ToolCallRequested = "ToolCallRequested";
    public const string ToolReceiptPublished = "ToolReceiptPublished";
    public const string VerifierStarted = "VerifierStarted";
    public const string VerifierFailed = "VerifierFailed";
    public const string VerifierPassed = "VerifierPassed";
    public const string CandidateOutputProduced = "CandidateOutputProduced";
    public const string ApprovedOutputReleased = "ApprovedOutputReleased";
    public const string OutputBlocked = "OutputBlocked";
    public const string PatchPublished = "PatchPublished";
    public const string FileFocusRequested = "FileFocusRequested";
    public const string ExternalEditorOpenRequested = "ExternalEditorOpenRequested";
    public const string RuntimeDisconnected = "RuntimeDisconnected";
    public const string RuntimeReconnected = "RuntimeReconnected";
    public const string SessionCompleted = "SessionCompleted";
}
