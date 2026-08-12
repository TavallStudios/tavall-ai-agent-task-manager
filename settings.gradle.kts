rootProject.name = "tavall-ai"

include(
    "tavall-ai-agent-core",
    "tavall-ai-agent-scheduler",
    "tavall-ai-agent-orchestration",
    "tavall-ai-agent-implementation",
    "tavall-ai-agent-review",
    "tavall-ai-agent-reconciliation",
    "tavall-ai-agent-recovery",
    "tavall-ai-agent-e2e",
    "tavall-ai-agent-architecture",
    "tavall-ai-agent-documentation",
    "tavall-ai-runtime",
)

sourceControl {
    gitRepository(uri("https://github.com/TavallStudios/function-catalog.git")) {
        producesModule("org.tavall:ai-core")
        producesModule("org.tavall:agent-runtime")
        producesModule("org.tavall:codex-agent-provider")
    }
}
