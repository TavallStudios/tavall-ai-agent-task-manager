rootProject.name = "tavall-ai"

include(
    "tavall-ai-bootstrap",
    "tavall-agent-scheduler",
    "tavall-agent-orchestration",
    "tavall-agent-implementation",
    "tavall-agent-review",
    "tavall-agent-reconciliation",
    "tavall-agent-e2e",
    "tavall-agent-architecture",
    "tavall-agent-documentation",
    "tavall-agent-builder",
    "tavall-ai-runtime-distributed-execution",
    "tavall-ai-runtime",
)

sourceControl {
    gitRepository(uri("https://github.com/TavallStudios/function-catalog.git")) {
        // Transitional ownership. agent-runtime and codex-agent-provider move into Tavall AI in a
        // stacked migration; Function Catalog will retain only callable-function/MCP infrastructure.
        producesModule("org.tavall:ai-core")
        producesModule("org.tavall:agent-runtime")
        producesModule("org.tavall:codex-agent-provider")
    }
}
