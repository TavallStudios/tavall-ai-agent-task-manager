plugins {
    application
}

application {
    mainClass.set("org.tavall.ai.runtime.TavallAIRuntimeMain")
}

dependencies {
    implementation(project(":tavall-ai-bootstrap"))
    implementation(project(":tavall-ai-runtime-distributed-execution"))

    runtimeOnly(project(":tavall-agent-scheduler"))
    runtimeOnly(project(":tavall-agent-orchestration"))
    runtimeOnly(project(":tavall-agent-implementation"))
    runtimeOnly(project(":tavall-agent-review"))
    runtimeOnly(project(":tavall-agent-reconciliation"))
    runtimeOnly(project(":tavall-agent-e2e"))
    runtimeOnly(project(":tavall-agent-architecture"))
    runtimeOnly(project(":tavall-agent-documentation"))
    runtimeOnly(project(":tavall-agent-builder"))

    // Transitional source-control dependencies. These AI runtime/provider modules move into this
    // repository in the next stacked migration; Function Catalog remains the function/MCP system.
    implementation("org.tavall:agent-runtime") {
        version { branch = "main" }
    }
    runtimeOnly("org.tavall:codex-agent-provider") {
        version { branch = "main" }
    }
}
