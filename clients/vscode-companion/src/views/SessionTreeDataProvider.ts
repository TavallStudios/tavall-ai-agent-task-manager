import * as vscode from "vscode";
import { SessionSummary } from "../contracts";

export class SessionTreeDataProvider implements vscode.TreeDataProvider<SessionTreeItem> {
  private readonly onDidChangeEmitter = new vscode.EventEmitter<void>();
  private sessions: SessionSummary[] = [];

  readonly onDidChangeTreeData = this.onDidChangeEmitter.event;

  updateSessions(items: SessionSummary[]): void {
    this.sessions = items;
    this.onDidChangeEmitter.fire();
  }

  getTreeItem(element: SessionTreeItem): vscode.TreeItem {
    return element;
  }

  getChildren(): SessionTreeItem[] {
    return this.sessions.map(session => new SessionTreeItem(session));
  }
}

export class SessionTreeItem extends vscode.TreeItem {
  constructor(readonly session: SessionSummary) {
    super(session.title, vscode.TreeItemCollapsibleState.None);
    this.description = session.lifecycleState;
    this.command = {
      command: "agentTaskManager.openSession",
      title: "Open Session",
      arguments: [session.sessionId]
    };
  }
}
