# Codex Desktop Session Protocol

This document defines the backend-facing protocol the WinUI client expects.

## 1. REST Endpoints

### `POST /api/codex-client/sessions`

Request:

```json
{
  "title": "Fix verifier regression",
  "projectKey": "agent-task-manager",
  "repoPath": "F:\\workspace\\AgentTaskManager",
  "workspaceRoot": "F:\\workspace\\AgentTaskManager",
  "profileKey": "workspace-default",
  "clientSurface": "DESKTOP",
  "workspaceScope": "REPOSITORY",
  "utilitySession": false,
  "createRuntime": true,
  "initialPrompt": "Inspect verifier failures in the selected repository."
}
```

Response: `SessionDetailDto`

### `POST /api/codex-client/sessions/{sessionId}/attach`

Use this when a device starts observing a session without taking ownership.

```json
{
  "deviceId": "desktop-4f18d2c5",
  "deviceName": "TJ-Workstation",
  "clientSurface": "DESKTOP",
  "hostName": "TJ-Workstation",
  "observeOnly": true
}
```

### `POST /api/codex-client/sessions/{sessionId}/resume`

Use this when a device requests ownership or handoff.

```json
{
  "deviceId": "desktop-4f18d2c5",
  "hostName": "TJ-Workstation",
  "requestOwnership": true,
  "allowRuntimeHandoff": true
}
```

### `POST /api/codex-client/sessions/{sessionId}/turns`

```json
{
  "promptText": "Run the clean Java approval loop and explain the verifier failure.",
  "requestedMode": "edit",
  "requiredReceiptKinds": [
    "repo-context",
    "validation",
    "patch-gate"
  ],
  "allowFileEdits": true
}
```

### `GET /api/codex-client/sessions/{sessionId}/events`

Query parameters:

- `afterEventId`
- `limit`

### `GET /api/codex-client/sessions/{sessionId}/events/stream`

Server-Sent Events stream.

Query parameters:

- `replayLimit`
- `afterEventId`

## 2. Runtime Event Ingestion

The local runtime-owning device should not write directly into canonical stores. It posts normalized runtime observations back to the backend.

### `POST /api/codex-client/sessions/{sessionId}/runtime/connected`

Payload:

```json
{
  "runtimeId": "rt_123",
  "connectionState": "CONNECTED",
  "transportKind": "WEBSOCKET",
  "authMode": "CHATGPT",
  "preferredModel": "gpt-5.3-codex",
  "endpointDescription": "ws://127.0.0.1:43127",
  "schemaVersion": "codex-app-server/v2",
  "lastHeartbeatAt": "2026-03-22T10:15:00Z"
}
```

The current desktop scaffold launches the runtime locally as:

```text
codex app-server --listen ws://127.0.0.1:{port} --session-source desktop
```

The verified request flow is:

1. `initialize`
2. `initialized`
3. `thread/start` or `thread/resume`
4. `turn/start`

### `POST /api/codex-client/sessions/{sessionId}/runtime/events`

Payload:

```json
{
  "runtimeId": "rt_123",
  "sequenceNumber": 41,
  "eventType": "TurnDeltaReceived",
  "threadId": "thread_abc",
  "turnId": "turn_xyz",
  "occurredAt": "2026-03-22T10:15:04Z",
  "summary": "Received assistant delta from Codex runtime.",
  "attributes": {
    "deltaKind": "agent-message",
    "chunkLength": 128
  },
  "rawNotificationName": "item/agentMessage/delta"
}
```

### `POST /api/codex-client/sessions/{sessionId}/runtime/disconnected`

Payload:

```json
{
  "runtimeId": "rt_123",
  "connectionState": "DEGRADED",
  "disconnectReason": "process-exited",
  "recoverable": true,
  "observedAt": "2026-03-22T10:18:00Z"
}
```

## 3. Event Envelope

Every canonical event stored by the backend uses the same envelope:

```json
{
  "eventId": "evt_123",
  "sessionId": "sess_123",
  "turnId": "turn_123",
  "eventType": "PatchPublished",
  "schemaVersion": "atm.codex-session.v1",
  "source": "backend",
  "occurredAt": "2026-03-22T10:16:00Z",
  "attributes": {
    "patchId": "patch_456",
    "changedFiles": 3
  },
  "summary": "Published candidate patch preview."
}
```

## 4. Session Detail Shape

`SessionDetailDto` should include:

- `summary`
- `workspaceBinding`
- `runtimeConnection`
- `runtimeLease`
- `devices`
- `turns`
- `toolReceipts`
- `verifierResults`
- `outputs`
- `patches`
- `fileFocusRequests`
- `memoryReferences`
- `recentEvents`

## 5. Event Semantics

### Session and lease events

- `SessionCreated`
- `SessionAttached`
- `SessionResumed`
- `WorkspaceBound`
- `RuntimeDisconnected`
- `RuntimeReconnected`
- `SessionCompleted`

### Turn and runtime events

- `ThreadStarted`
- `TurnStarted`
- `TurnDeltaReceived`
- `ToolCallRequested`
- `PatchPublished`
- `FileFocusRequested`

### Gate and output events

- `ToolReceiptPublished`
- `VerifierStarted`
- `VerifierFailed`
- `VerifierPassed`
- `CandidateOutputProduced`
- `ApprovedOutputReleased`
- `OutputBlocked`

## 6. Reconnect Rules

The desktop app should:

1. Keep the last durable backend `eventId`.
2. Reopen the SSE stream with replay.
3. Refresh `SessionDetailDto` after reconnect.
4. Resume observation first.
5. Request ownership only through `POST /resume`.

The backend should:

1. Treat SSE as a replayable projection over canonical event storage.
2. Treat runtime ownership as a lease, not an implied side effect of streaming.
3. Reject final output release when required receipts or verifier approval are missing.
4. Accept runtime reconnect streams starting from `afterEventId` so the desktop client can resume without assuming in-memory continuity.
