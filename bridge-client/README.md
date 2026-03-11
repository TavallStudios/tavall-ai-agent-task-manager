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
