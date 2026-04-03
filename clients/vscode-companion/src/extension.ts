import * as vscode from "vscode";
import { SessionApiClient } from "./backend/SessionApiClient";
import { SessionPanelProvider } from "./panels/SessionPanelProvider";
import { SessionTreeDataProvider } from "./views/SessionTreeDataProvider";

export function activate(context: vscode.ExtensionContext): void {
  const apiClient = new SessionApiClient(context);
  const panelProvider = new SessionPanelProvider();
  const treeProvider = new SessionTreeDataProvider();
  vscode.window.registerTreeDataProvider("agentTaskManager.sessions", treeProvider);

  context.subscriptions.push(
    vscode.commands.registerCommand("agentTaskManager.signIn", async () => {
      const url = await vscode.window.showInputBox({ prompt: "Backend URL", value: "http://localhost:9000" });
      if (url) {
        await context.globalState.update("atm.backendUrl", url);
      }
    }),
    vscode.commands.registerCommand("agentTaskManager.listSessions", async () => {
      const response = await apiClient.listSessions();
      treeProvider.updateSessions(response.items);
    }),
    vscode.commands.registerCommand("agentTaskManager.openSession", async (sessionId: string) => {
      const detail = await apiClient.getSession(sessionId);
      panelProvider.show(detail);
    }),
    vscode.commands.registerCommand("agentTaskManager.submitTurn", async (sessionId: string) => {
      void sessionId;
      vscode.window.showWarningMessage("Turn submission scaffold not wired yet.");
    })
  );
}

export function deactivate(): void {}
