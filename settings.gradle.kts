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
    gitRepository(uri("https://github.com/TavallStudios/tavall-cache.git")) {
        producesModule("org.tavall:abstract-cache-semantic")
        producesModule("org.tavall:abstract-cache-storage-memory")
    }
    gitRepository(uri("https://github.com/TavallStudios/tavall-concurrency.git")) { producesModule("org.tavall:tavall-concurrency") }
    gitRepository(uri("https://github.com/TavallStudios/tavall-database.git")) { producesModule("org.tavall:tavall-database-core-contracts") }
    gitRepository(uri("https://github.com/TavallStudios/tavall-di.git")) { producesModule("org.tavall:tavall-di") }
    gitRepository(uri("https://github.com/TavallStudios/tavall-eventbus.git")) { producesModule("org.tavall:tavall-eventbus") }
    gitRepository(uri("https://github.com/TavallStudios/tavall-logging.git")) { producesModule("org.tavall:tavall-logging") }
    gitRepository(uri("https://github.com/TavallStudios/tavall-reflection.git")) { producesModule("org.tavall:tavall-reflection") }
    gitRepository(uri("https://github.com/TavallStudios/tavall-registry.git")) { producesModule("org.tavall:tavall-registry") }
    gitRepository(uri("https://github.com/TavallStudios/tavall-scheduler.git")) { producesModule("org.tavall:tavall-scheduler") }
}
