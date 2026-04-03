export interface SessionSummary {
  sessionId: string;
  title: string;
  repoPath: string;
  workspaceRoot: string;
  lifecycleState: string;
  runtimeConnectionState: string;
  outputReleaseState: string;
}

export interface SessionEventEnvelope {
  eventId: string;
  sessionId: string;
  turnId?: string;
  eventType: string;
  schemaVersion: string;
  source: string;
  summary: string;
  attributes: Record<string, unknown>;
  occurredAt: string;
}

export interface SessionDetail {
  summary: SessionSummary;
  recentEvents: SessionEventEnvelope[];
}

export interface SessionListResponse {
  items: SessionSummary[];
}

export interface EventListResponse {
  items: SessionEventEnvelope[];
}
