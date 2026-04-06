# Computer-Use Runner

AgentTaskManager treats Hytale automation as an external-runner problem, not an operator-desktop problem. The Java app remains the control plane. A separate Windows machine or VM runs the automation host, owns the Hytale install and login state, and accepts capture and input commands over HTTP.

The browser control surface for this flow lives at `/computer-use` on the AgentTaskManager web app. It provides a simple pilot window with streamed frame refresh, synthetic cursor rendering, launcher/client controls, and remote key or mouse injection.

## Components

- Control plane: `tavall-ai-app`
- Windows runner host: `clients/desktop/AgentTaskManager.AutomationHost`
- Hytale-first MCP surface:
  - `registerComputerUseRunner`
  - `listComputerUseRunners`
  - `startComputerUseSession`
  - `launchComputerUseProcess`
  - `captureComputerUseWindow`
  - `sendComputerUseInput`
  - `waitForComputerUseVisionMatch`
  - `stopComputerUseSession`

## Runner Setup

On the external Windows machine:

1. Install the Hytale launcher and client.
2. Build the desktop host:

```powershell
& 'C:\Program Files\dotnet\dotnet.exe' build F:\workspace\AgentTaskManager\clients\desktop\AgentTaskManager.Desktop.sln -c Debug -p:Platform=x64
```

3. Start the host in HTTP mode:

```powershell
F:\workspace\AgentTaskManager\clients\desktop\start-hytale-runner.ps1 `
  -Build `
  -HttpPort 54123 `
  -HytaleLauncherPath 'F:\Games\Hytale\Launcher\HytaleLauncher.exe' `
  -HytaleClientPath 'F:\Games\Hytale\Client\Hytale.exe'
```

That wrapper starts `start-automation-host.ps1` in HTTP mode and prints a ready-to-use `registerComputerUseRunner` payload.

## Control Plane Config

AgentTaskManager keeps the Hytale runner profile deterministic in `app.computer-use.*`.

Important properties:

- `app.computer-use.runner-command-path`
- `app.computer-use.runner-auth-token`
- `app.computer-use.runner-lease-ttl-seconds`
- `app.computer-use.vision-poll-interval-ms`
- `app.computer-use.hytale.launcher-path`
- `app.computer-use.hytale.client-path`
- `app.computer-use.hytale.server-target`
- `app.computer-use.hytale.default-chart-id`
- `app.computer-use.hytale.visual-anchors.launcherReady`
- `app.computer-use.hytale.visual-anchors.clientReady`
- `app.computer-use.hytale.visual-anchors.worldJoined`
- `app.computer-use.hytale.visual-anchors.songSelect`
- `app.computer-use.hytale.visual-anchors.gameplayAssets`
- `app.computer-use.hytale.gameplay-keybinds.lane1`
- `app.computer-use.hytale.gameplay-keybinds.lane2`
- `app.computer-use.hytale.gameplay-keybinds.lane3`
- `app.computer-use.hytale.gameplay-keybinds.lane4`

All of these can be overridden with the matching `AGENT_TASK_MANAGER_*` environment variables exposed in `application.properties`.

## Hytale Scenarios

Supported scenario ids:

- `hytale/launch-and-join-smoke`
  Launch launcher, launch client, join server.
- `hytale/gameplay-assets-visible`
  Join server, start HyRhythm, and prove gameplay assets rendered.
- `hytale/chart-start-stable`
  Start the configured chart and confirm the client stays stable.
- `hytale/note-hit-interaction`
  Send gameplay inputs and prove non-zero interaction.

Each session persists a `scenarioDefinition` block in session metadata. That metadata contains:

- description
- ordered step list
- expected artifacts
- pass-fail gates
- artifact policy

Workers should use that metadata as the authoritative checklist instead of inventing their own flow.

## Artifact Expectations

The runner session is fail-closed. A session is not complete unless the required artifacts and gates are satisfied.

Expected artifact classes include:

- launcher-ready
- client-ready
- world-joined
- song-select
- chart-start
- gameplay-assets
- note-hit-result
- gameplay-summary
- vision-match

Capture payloads are persisted through the standard session artifact tables and Mongo artifact document store.

The browser pilot window uses non-persisted live frame previews for responsiveness and persists artifacts only when the scenario or tool flow requests them.

## Runner Commands

The external runner host supports these game-oriented commands in addition to the older UI Automation surface:

- `capture_region`
- `capture_stream_frame`
- `match_template`
- `send_key_batch`
- `send_mouse_batch`
- `launch_process`

These commands are enough for windowed or borderless Hytale. Exclusive fullscreen remains out of scope for v1.

The canonical runner command endpoint is `/api/automation/command` with capabilities at
`/api/automation/capabilities`. Compatibility aliases (`/request`, `/execute`) are temporary.

