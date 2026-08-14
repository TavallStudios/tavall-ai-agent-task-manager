plugins {
    application
}

application {
    mainClass.set("org.tavall.ai.runtime.TavallAIRuntimeMain")
}

dependencies {
    implementation(project(":tavall-ai-bootstrap"))
    implementation(project(":tavall-ai-runtime-model-execution"))
    implementation(project(":tavall-ai-runtime-distributed-execution"))
    runtimeOnly(project(":tavall-ai-runtime-codex"))

    runtimeOnly(project(":tavall-agent-scheduler"))
    runtimeOnly(project(":tavall-agent-orchestration"))
    runtimeOnly(project(":tavall-agent-implementation"))
    runtimeOnly(project(":tavall-agent-review"))
    runtimeOnly(project(":tavall-agent-reconciliation"))
    runtimeOnly(project(":tavall-agent-e2e"))
    runtimeOnly(project(":tavall-agent-architecture"))
    runtimeOnly(project(":tavall-agent-documentation"))
    runtimeOnly(project(":tavall-agent-builder"))
}
