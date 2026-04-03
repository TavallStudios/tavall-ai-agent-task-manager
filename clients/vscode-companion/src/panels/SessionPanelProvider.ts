import * as vscode from "vscode";
import { SessionDetail } from "../contracts";

export class SessionPanelProvider {
  private panel: vscode.WebviewPanel | undefined;

  show(detail: SessionDetail): void {
    this.panel ??= vscode.window.createWebviewPanel(
      "agentTaskManager.session",
      detail.summary.title,
      vscode.ViewColumn.Active,
      { enableScripts: false }
    );
    this.panel.webview.html = this.render(detail);
    this.panel.reveal();
  }

  private render(detail: SessionDetail): string {
    const events = detail.recentEvents
      .map(event => `<li><strong>${event.eventType}</strong>: ${event.summary}</li>`)
      .join("");
    return `
      <html>
        <body>
          <h1>${detail.summary.title}</h1>
          <p>${detail.summary.workspaceRoot}</p>
          <p>Runtime: ${detail.summary.runtimeConnectionState}</p>
          <ul>${events}</ul>
        </body>
      </html>`;
  }
}
