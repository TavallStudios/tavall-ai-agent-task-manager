package io.agenttaskmanager.companion.model

data class SessionSummary(
    val sessionId: String,
    val title: String,
    val repoPath: String,
    val workspaceRoot: String,
    val lifecycleState: String,
    val runtimeConnectionState: String,
    val outputReleaseState: String
)

data class SessionEventEnvelope(
    val eventId: String,
    val sessionId: String,
    val turnId: String?,
    val eventType: String,
    val source: String,
    val summary: String,
    val occurredAt: String
)

data class SessionDetail(
    val summary: SessionSummary,
    val recentEvents: List<SessionEventEnvelope>
)
