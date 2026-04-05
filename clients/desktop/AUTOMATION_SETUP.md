# Desktop Automation Setup

`AgentTaskManager.AutomationHost` is a local Windows automation bridge for visual and functional debugging on this machine.

It supports two control modes:

- non-intrusive mode for standard desktop apps through UI Automation plus `PrintWindow` capture
- intrusive fallback mode for apps that only respond to foreground input
- optional HTTP listener mode for external runner boundaries and remote orchestration

## What Works Well

- WinUI, WPF, WinForms, many native Win32 apps
- window discovery and screenshot capture without taking over the desktop
- UI tree dumps, automation-id lookup, invoke, select, and value-setting
- raw-UIA fallback for Chromium or WebView launchers when controls are missing from the default control view
- non-intrusive window repositioning for multi-monitor automation layouts

## What Does Not Fully Isolate

- games and custom-rendered apps such as Hytale
- anything that depends on raw input, DirectInput, anti-cheat, or exclusive fullscreen

For those apps, the bridge can still help with screenshots and targeted fallback clicks or text entry, but that path is intrusive and will compete with your own mouse or keyboard. If you want true parallel use for a game-class app, you still need a separate machine, VM session with its own GPU, or another isolated display/session strategy.

## Build

```powershell
& 'C:\Program Files\dotnet\dotnet.exe' build F:\workspace\AgentTaskManager\clients\desktop\AgentTaskManager.Desktop.sln -c Debug -p:Platform=x64
```

## Start The Host

```powershell
F:\workspace\AgentTaskManager\clients\desktop\start-automation-host.ps1 -Build
```

The host reads one JSON request per line from stdin and writes one JSON response per line to stdout.

To run it as an HTTP listener instead of stdio:

```powershell
F:\workspace\AgentTaskManager\clients\desktop\start-automation-host.ps1 -Build -HttpPort 54123
```

Canonical HTTP endpoints:

- `POST /api/automation/command`
- `GET /api/automation/health`
- `GET /api/automation/capabilities`
- `POST /api/automation/lease/heartbeat`

Compatibility aliases remain for one release cycle:

- `POST /request`
- `POST /execute`
- `GET /health`

## Useful Commands

List windows:

```json
{"id":"list","command":"list_windows","parameters":{"includeInvisible":false}}
```

Wait for AgentTaskManager:

```json
{"id":"wait","command":"wait_for_window","parameters":{"window":{"processName":"AgentTaskManager.Desktop"},"timeoutMs":10000}}
```

Capture a window:

```json
{"id":"capture","command":"capture_window","parameters":{"window":{"titleContains":"AgentTaskManager Desktop"},"outputPath":"F:\\workspace\\AgentTaskManager\\clients\\desktop\\automation-smoke.png"}}
```

Capture a screen region:

```json
{"id":"region","command":"capture_region","parameters":{"left":0,"top":0,"width":1280,"height":720,"outputPath":"F:\\workspace\\AgentTaskManager\\clients\\desktop\\region-smoke.png"}}
```

Capture a live frame from a window client region:

```json
{"id":"frame","command":"capture_stream_frame","parameters":{"window":{"processName":"AgentTaskManager.Desktop"},"region":{"left":0,"top":0,"width":800,"height":600},"outputPath":"F:\\workspace\\AgentTaskManager\\clients\\desktop\\frame-smoke.png"}}
```

When a window is supplied, the `region` is interpreted in client coordinates. Without a window, it is treated as screen coordinates.

Match a template inside a captured frame:

```json
{"id":"match","command":"match_template","parameters":{"window":{"processName":"AgentTaskManager.Desktop"},"templatePath":"F:\\workspace\\AgentTaskManager\\clients\\desktop\\button-template.png","threshold":0.92,"outputPath":"F:\\workspace\\AgentTaskManager\\clients\\desktop\\match-smoke.png"}}
```

The matcher uses the same capture-source rules as `capture_stream_frame` and returns the best match bounds plus a confidence score.

Find an element by automation id:

```json
{"id":"find","command":"find_elements","parameters":{"window":{"processName":"AgentTaskManager.Desktop"},"selector":{"automationId":"Button_SignIn"},"maxDepth":8,"maxResults":5}}
```

Set a text field without foreground input:

```json
{"id":"set","command":"set_value","parameters":{"window":{"processName":"AgentTaskManager.Desktop"},"selector":{"automationId":"Field_User_name"},"value":"automation-smoke"}}
```

Invoke a button without moving the real mouse:

```json
{"id":"invoke","command":"invoke_element","parameters":{"window":{"processName":"AgentTaskManager.Desktop"},"selector":{"automationId":"Nav_Sessions"}}}
```

Move a window onto a specific monitor layout without activating it:

```json
{"id":"move","command":"move_window","parameters":{"window":{"processName":"hytale-launcher"},"left":-2480,"top":80,"width":1440,"height":980,"activateWindow":false}}
```

Fallback click for apps that ignore UI Automation:

```json
{"id":"click","command":"click_point","parameters":{"window":{"processName":"notepad"},"x":300,"y":300,"mode":"windowMessage"}}
```

Foreground fallback text entry:

```json
{"id":"type","command":"send_text","parameters":{"window":{"processName":"notepad"},"text":"foreground fallback","activateWindow":true}}
```

Batch key input for games and custom renderers:

```json
{"id":"keys","command":"send_key_batch","parameters":{"window":{"processName":"notepad"},"events":[{"key":"W","action":"down"},{"key":"W","action":"up"},{"key":"Space","action":"press"}]}}
```

Batch mouse input with explicit screen coordinates:

```json
{"id":"mouse","command":"send_mouse_batch","parameters":{"events":[{"action":"move","x":900,"y":500,"coordinates":"screen"},{"action":"down","button":"left","x":900,"y":500,"coordinates":"screen"},{"action":"up","button":"left","x":900,"y":500,"coordinates":"screen"}]}}
```

## External Runner Boundary

When another process should own orchestration, start the host in HTTP mode and post the same request envelope to it.

```powershell
$response = Invoke-RestMethod `
  -Method Post `
  -Uri 'http://127.0.0.1:54123/api/automation/command' `
  -ContentType 'application/json' `
  -Body '{"id":"ping","command":"ping","parameters":{}}'

$response
```

That keeps the worker boundary outside the desktop app while preserving the same command set and JSON shapes.

## Hytale Runner Bootstrap

For Hytale, use a dedicated Windows machine or VM and start the host through the Hytale wrapper instead of binding raw game automation to the operator desktop:

```powershell
F:\workspace\AgentTaskManager\clients\desktop\start-hytale-runner.ps1 `
  -Build `
  -HttpPort 54123 `
  -HytaleLauncherPath 'F:\Games\Hytale\Launcher\HytaleLauncher.exe' `
  -HytaleClientPath 'F:\Games\Hytale\Client\Hytale.exe'
```

The wrapper prints a suggested `registerComputerUseRunner` payload for the central MCP tools and then starts the same automation host in HTTP mode.

Once the runner is registered, open the AgentTaskManager web app at `/computer-use` to use the browser-side pilot window. That surface polls remote frames, renders a synthetic cursor, and forwards mouse or keyboard input to the runner session.

AgentTaskManager expects Hytale sessions to run through the dedicated computer-use tool surface:

- `registerComputerUseRunner`
- `startComputerUseSession`
- `launchComputerUseProcess`
- `captureComputerUseWindow`
- `sendComputerUseInput`
- `waitForComputerUseVisionMatch`
- `stopComputerUseSession`

The built-in Hytale scenario ids are:

- `hytale/launch-and-join-smoke`
- `hytale/gameplay-assets-visible`
- `hytale/chart-start-stable`
- `hytale/note-hit-interaction`

Visual anchors and gameplay keybinds are configured on the Java side under `app.computer-use.hytale.*`, so the runner machine does not need any user-global Codex configuration to participate.

## Smoke Test

```powershell
F:\workspace\AgentTaskManager\clients\desktop\run-automation-smoke.ps1
```

That script builds the desktop solution, launches the AgentTaskManager desktop app, verifies automation IDs, writes to the username field, captures a screenshot, and closes the smoke instance it started.

For the local Hytale launcher-to-client path on a secondary monitor:

```powershell
F:\workspace\AgentTaskManager\clients\desktop\run-hytale-local-smoke.ps1 -DisplayDeviceName DISPLAY2
```

That script keeps the launcher and client on the requested display, invokes the launcher `PLAY` button through UI Automation, retries once if an auth-error client window appears, and writes launcher/client captures to `F:\workspace\_codex_temp`.

For local Creative Tools and Asset Editor attachment without foregrounding the
client, use:

```powershell
F:\workspace\AgentTaskManager\clients\desktop\run-hytale-creative-tools-smoke.ps1 -DisplayDeviceName DISPLAY2
```

That script keeps the live client and asset editor on the requested display,
uses non-intrusive window-message clicks when the Creative Tools page is already
open, and otherwise falls back to the latest authenticated asset-editor launch
command recorded by the client logs. It also reports foreground-window before
and after the run so focus regressions are obvious. Use `-SkipBuild` when the
host binary is already built and you want to avoid a restore/build step.
