plugins {
    application
}

application {
    mainClass.set("org.tavall.ai.runtime.TavallAIRuntimeMain")
}

dependencies {
    implementation(project(":tavall-ai-agent-core"))
    implementation(project(":tavall-ai-module-distributed-execution"))

    runtimeOnly(project(":tavall-ai-agent-scheduler"))
    runtimeOnly(project(":tavall-ai-agent-orchestration"))
    runtimeOnly(project(":tavall-ai-agent-implementation"))
    runtimeOnly(project(":tavall-ai-agent-review"))
    runtimeOnly(project(":tavall-ai-agent-reconciliation"))
    runtimeOnly(project(":tavall-ai-agent-e2e"))
    runtimeOnly(project(":tavall-ai-agent-architecture"))
    runtimeOnly(project(":tavall-ai-agent-documentation"))
    runtimeOnly(project(":tavall-ai-module-builder"))

    // Transitional source-control dependencies. These AI runtime/provider modules move into this
    // repository in the next stacked migration; Function Catalog remains the function/MCP system.
    implementation("org.tavall:agent-runtime") {
        version { branch = "main" }
    }
    runtimeOnly("org.tavall:codex-agent-provider") {
        version { branch = "main" }
    }
}
