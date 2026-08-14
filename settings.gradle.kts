rootProject.name = "tavall-ai"

include(
    "tavall-ai-agent-core",
    "tavall-ai-agent-scheduler",
    "tavall-ai-agent-orchestration",
    "tavall-ai-agent-implementation",
    "tavall-ai-agent-review",
    "tavall-ai-agent-reconciliation",
    "tavall-ai-agent-e2e",
    "tavall-ai-agent-architecture",
    "tavall-ai-agent-documentation",
    "tavall-ai-module-distributed-execution",
    "tavall-ai-module-builder",
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
