plugins {
    base
}

group = "org.tavall.ai"
extra["versionTagPrefix"] = "tavall-ai"
extra["fallbackVersion"] = "0.1.0"
apply(from = "gradle/git-version.gradle.kts")
version = extra["gitVersion"] as String

val junitVersion = "5.12.2"
val agentProviderService = "org.tavall.agent.TavallAgentProvider"
val moduleProviderService = "org.tavall.ai.bootstrap.TavallAIModuleProvider"
val agentProjects = listOf(
    "tavall-agent-scheduler",
    "tavall-agent-orchestration",
    "tavall-agent-implementation",
    "tavall-agent-review",
    "tavall-agent-reconciliation",
    "tavall-agent-e2e",
    "tavall-agent-architecture",
    "tavall-agent-documentation",
    "tavall-agent-builder",
)
val runtimeModuleProjects = listOf(
    "tavall-ai-runtime-distributed-execution",
)

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
        withSourcesJar()
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:$junitVersion"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 25
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        maxParallelForks = 1
        maxHeapSize = "256m"
    }

    tasks.withType<Jar>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                artifactId = project.name
            }
        }
        repositories {
            val token = providers.environmentVariable("GITHUB_TOKEN")
            if (token.isPresent) {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/TavallStudios/tavall-ai-agent-task-manager")
                    credentials {
                        username = providers.environmentVariable("GITHUB_ACTOR").orNull
                        password = token.get()
                    }
                }
            }
        }
    }
}

configure(agentProjects.map(::project)) {
    dependencies {
        "api"(project(":tavall-ai-bootstrap"))
    }

    val verifyAgentDescriptor = tasks.register("verifyAgentDescriptor") {
        group = "verification"
        description = "Verifies this Tavall agent publishes one provider and one canonical ROLE.md."
        doLast {
            val serviceFile = file("src/main/resources/META-INF/services/$agentProviderService")
            check(serviceFile.isFile) {
                "Missing Tavall agent ServiceLoader registration for $path: $serviceFile"
            }
            val providers = serviceFile.readLines()
                .map(String::trim)
                .filter(String::isNotBlank)
                .filterNot { it.startsWith("#") }
            check(providers.size == 1) {
                "Expected exactly one Tavall agent provider for $path, found ${providers.size}"
            }

            val roleDocuments = fileTree("src/main/resources") {
                include("**/ROLE.md")
            }.files
            check(roleDocuments.size == 1) {
                "Expected exactly one canonical ROLE.md for $path, found ${roleDocuments.size}"
            }
            check(roleDocuments.single().readText().isNotBlank()) {
                "Canonical ROLE.md must not be blank for $path"
            }
        }
    }

    tasks.named("check") {
        dependsOn(verifyAgentDescriptor)
    }
}

project(":tavall-agent-review") {
    dependencies {
        "implementation"("org.tavall:abstract-cache-semantic") { version { branch = "main" } }
        "runtimeOnly"("org.tavall:abstract-cache-storage-memory") { version { branch = "main" } }
        "implementation"("org.tavall:tavall-concurrency") { version { branch = "main" } }
        "implementation"("org.tavall:tavall-database-core-contracts") { version { branch = "main" } }
        "implementation"("org.tavall:tavall-di") { version { branch = "main" } }
        "implementation"("org.tavall:tavall-eventbus") { version { branch = "main" } }
        "implementation"("org.tavall:tavall-logging") { version { branch = "main" } }
        "implementation"("org.tavall:tavall-reflection") { version { branch = "main" } }
        "implementation"("org.tavall:tavall-registry") { version { branch = "main" } }
        "implementation"("org.tavall:tavall-scheduler") { version { branch = "main" } }
    }
}

configure(runtimeModuleProjects.map(::project)) {
    val verifyRuntimeModuleDescriptor = tasks.register("verifyRuntimeModuleDescriptor") {
        group = "verification"
        description = "Verifies this Tavall AI runtime capability module publishes exactly one module provider."
        doLast {
            val serviceFile = file("src/main/resources/META-INF/services/$moduleProviderService")
            check(serviceFile.isFile) {
                "Missing Tavall AI runtime-module ServiceLoader registration for $path: $serviceFile"
            }
            val providers = serviceFile.readLines()
                .map(String::trim)
                .filter(String::isNotBlank)
                .filterNot { it.startsWith("#") }
            check(providers.size == 1) {
                "Expected exactly one Tavall AI runtime module provider for $path, found ${providers.size}"
            }
        }
    }

    tasks.named("check") {
        dependsOn(verifyRuntimeModuleDescriptor)
    }
}

val stageDistribution = tasks.register<Sync>("stageDistribution") {
    group = "distribution"
    description = "Stages the Tavall AI runtime distribution and ChatGPT plugin as one inspectable release candidate."

    dependsOn(":tavall-ai-runtime:installDist")

    into(layout.buildDirectory.dir("stage/tavall-ai"))
    into("runtime") {
        from(project(":tavall-ai-runtime").layout.buildDirectory.dir("install/tavall-ai-runtime"))
    }
    into("plugins/tavall-ai") {
        from(layout.projectDirectory.dir("plugins/tavall-ai"))
    }
}

val verifyTavallAISystem = tasks.register("verifyTavallAISystem") {
    group = "verification"
    description = "Runs checks for Tavall AI bootstrap, agents, runtime modules, and runtimes."
    dependsOn(subprojects.map { it.tasks.named("check") })
}

tasks.named("check") {
    dependsOn(verifyTavallAISystem)
}
