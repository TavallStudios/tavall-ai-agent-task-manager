dependencies {
    api(project(":tavall-ai-bootstrap"))
    api(project(":tavall-ai-runtime-project-context"))
    api("org.tavall:ai-core") {
        version { branch = "main" }
    }
}
