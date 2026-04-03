# Repository Guidelines

- Scope: AgentTaskManager is the shared MCP-first harness and task runtime. Prefer fixing tool registration, stdio launchers, validation flow, and clean-Java/runtime wiring directly instead of layering one-off workarounds on top.
- Runtime: keep launcher behavior deterministic and debuggable. Stdio MCP entrypoints must work from non-repo working directories because deployed wrappers run from shared bin locations.
- Architecture: prefer small, explicit changes in the owning module over broad cross-module refactors when repairing a broken tool path.
- Testing: add focused regression coverage for launcher/config/runtime bugs so redeploys can be validated before shipping jars.
- Deployment: when changing a deployed MCP tool, rebuild the owning module and verify the launched jar directly before updating remote wrappers or config.
