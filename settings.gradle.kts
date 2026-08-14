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
    "tavall-ai-runtime-model-execution",
    "tavall-ai-runtime-codex",
    "tavall-ai-runtime-distributed-execution",
    "tavall-ai-runtime",
)

sourceControl {
    gitRepository(uri("https://github.com/TavallStudios/function-catalog.git")) {
        // Function Catalog remains the typed function/schema/view/MCP system consumed by Tavall AI.
        // Actual AI model runtime/provider implementations are owned by this repository.
        producesModule("org.tavall:ai-core")
    }
}
