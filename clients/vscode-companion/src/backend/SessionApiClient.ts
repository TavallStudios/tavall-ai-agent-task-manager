import * as vscode from "vscode";
import { EventListResponse, SessionDetail, SessionListResponse } from "../contracts";

export class SessionApiClient {
  constructor(private readonly context: vscode.ExtensionContext) {}

  async listSessions(): Promise<SessionListResponse> {
    return this.getJson<SessionListResponse>("/api/codex-client/sessions");
  }

  async getSession(sessionId: string): Promise<SessionDetail> {
    return this.getJson<SessionDetail>(`/api/codex-client/sessions/${sessionId}`);
  }

  async listEvents(sessionId: string): Promise<EventListResponse> {
    return this.getJson<EventListResponse>(`/api/codex-client/sessions/${sessionId}/events`);
  }

  private async getJson<T>(path: string): Promise<T> {
    const endpoint = this.context.globalState.get<string>("atm.backendUrl") ?? "http://localhost:9000";
    const response = await fetch(`${endpoint}${path}`);
    if (!response.ok) {
      throw new Error(`Backend request failed: ${response.status} ${response.statusText}`);
    }
    return (await response.json()) as T;
  }
}
