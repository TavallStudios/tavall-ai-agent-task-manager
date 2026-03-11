Local bridge for AgentTaskManager.

Run this on the same machine as your IDE and Codex installation.

Example:

```bash
python3 bridge-client/agent_task_manager_local_bridge.py \
  --base-url https://docs.tavall.org/agent-task-manager \
  --username agent \
  --password 'your-password' \
  --bridge-target local-ide \
  --agent-id windows-ide-bridge \
  --client-name "Windows Codex IDE Bridge" \
  --codex-command "$HOME/.local/bin/codex"
```

If your `codex` command is a wrapper, also set `CODEX_REAL_BIN` so the bridge can find the real CLI binary in non-interactive environments.

Windows bundle:

- Copy [agent-task-manager-bridge.config.example.json](/srv/AgentTaskManager/bridge-client/windows/agent-task-manager-bridge.config.example.json) to `agent-task-manager-bridge.config.json`
- Fill in the real `password`, `codexCommand`, and `codexRealBin`
- Start it manually with [start-local-bridge.ps1](/srv/AgentTaskManager/bridge-client/windows/start-local-bridge.ps1)
- Or register it at logon with [install-logon-task.ps1](/srv/AgentTaskManager/bridge-client/windows/install-logon-task.ps1)

Example on Windows PowerShell:

```powershell
Set-Location F:\workspace\AgentTaskManager\bridge-client\windows
Copy-Item .\agent-task-manager-bridge.config.example.json .\agent-task-manager-bridge.config.json
powershell -ExecutionPolicy Bypass -File .\start-local-bridge.ps1
```
