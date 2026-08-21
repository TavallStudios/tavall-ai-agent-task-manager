# ChatGPT Cloud MCP gateway

The ChatGPT tunnel reaches one MCP location: the existing AgentTaskManager
Java process. The tunnel launches a standard Unix-socket client which forwards
stdio to the process; AgentTaskManager gives those streams directly to the
official Java MCP SDK. No second AgentTaskManager JVM, HTTP forwarder, nested
MCP server, proxy tool cache, or direct repository-execution fallback is on
this route.

The gateway is opt-in. A deployment must set all of these values together:

| Environment variable | Purpose |
| --- | --- |
| `AGENT_TASK_MANAGER_CHATGPT_MCP_GATEWAY_ENABLED` | Enables the dedicated gateway. |
| `AGENT_TASK_MANAGER_CHATGPT_MCP_SOCKET_PATH` | Absolute private Unix socket path. |
| `AGENT_TASK_MANAGER_CHATGPT_MCP_SOCKET_GROUP` | Group granted read/write access to the socket. |
| `TAVALL_CLOUD_NODE_ID` | CONTROL node identity. |
| `TAVALL_CLOUD_SECRET` | Existing CONTROL secret file path. |
| `TAVALL_CLOUD_SOCKET` | Existing CONTROL Unix socket path. |
| `TAVALL_CLOUD_MAX_FRAME_BYTES` | Existing bounded CONTROL frame limit. |

Each accepted tunnel connection creates its own CONTROL client, performs the
normal Cloud preflight, and then publishes only the authenticated typed Tavall
Cloud catalog. The session reports `tools.listChanged=true` and an identifiable
`1.1.3-agent-gateway-59+<generation>` version. Losing CONTROL closes the MCP
connection; connecting a replacement tunnel session closes the old one.

The gateway intentionally does not expose AgentTaskManager's normal harness
catalog. It is a Cloud ingress profile, not a merger of unrelated MCP tools.
Graphiti and Graphify remain independent memory-plane services and are not
routed through this socket.

Validate a staged distribution before deployment with:

```bash
./gradlew --no-daemon :tavall-ai-app:test --tests org.tavall.ai.app.mcp.ChatGPTMcpUnixSocketGatewayTest
./gradlew --no-daemon stageDistribution
```

The native integration test opens a Unix socket, negotiates the modern MCP
protocol through `/usr/bin/nc`, verifies `tools.listChanged`, lists a real SDK
tool, invokes it, and confirms the socket is removed at shutdown.
