# Companion Migration (Desktop-First Cutover)

AgentTaskManager now treats the WinUI desktop client as the first-party operator surface.

VS Code and IntelliJ companion modules have been removed from first-party builds and should not be used for new operational workflows.

## Migration mapping

- Companion session list and resume actions -> Desktop `Work` surface
- Companion operation launch/debug actions -> Desktop `Operations` surface
- Companion remote connection setup -> Desktop `Remote` surface
- Companion model/profile/tool settings -> Desktop `Settings` surface

## Operator defaults

- Remote databases stay authoritative for persisted control-plane state.
- Runner bridges remain required for OS/UI execution on remote hosts.
- Desktop is the canonical path for operations, remote control, and settings.
