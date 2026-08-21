plugins {
    base
}

group = "org.tavall.ai"
extra["versionTagPrefix"] = "tavall-ai"
extra["fallbackVersion"] = "0.1.0"
apply(from = "gradle/git-version.gradle.kts")
version = extra["gitVersion"] as String

val junitVersion = "5.12.2"
val tavallPlatformVersion = "1.0.0"
val agentProviderIndex = "META-INF/tavall/agent-provider"
val moduleProviderIndex = "META-INF/tavall/runtime-module-provider"
val legacyAgentProviderService = "META-INF/services/org.tavall.agent.TavallAgentProvider"
val legacyModuleProviderService = "META-INF/services/org.tavall.ai.bootstrap.TavallAIModuleProvider"
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
        val githubToken = providers.environmentVariable("GITHUB_TOKEN").orNull
        if (!githubToken.isNullOrBlank()) {
            listOf(
                "tavall-di",
                "tavall-registry",
                "tavall-concurrency",
                "tavall-logging",
            ).forEach { repository ->
                maven("https://maven.pkg.github.com/TavallStudios/$repository") {
                    name = "github${repository.replace("-", "")}"
                    credentials {
                        username = providers.environmentVariable("GITHUB_ACTOR").orElse("github").get()
                        password = githubToken
                    }
                }
            }
        }
    }

    dependencies {
        "implementation"("org.tavall:tavall-di:$tavallPlatformVersion")
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

project(":tavall-ai-bootstrap") {
    dependencies {
        "api"("org.tavall:tavall-registry:$tavallPlatformVersion")
    }
}

project(":tavall-ai-runtime") {
    dependencies {
        "implementation"("org.tavall:tavall-concurrency:$tavallPlatformVersion")
        "implementation"("org.tavall:tavall-logging:$tavallPlatformVersion")
    }
}

configure(agentProjects.map(::project)) {
    dependencies {
        "api"(project(":tavall-ai-bootstrap"))
    }

    val verifyAgentDescriptor = tasks.register("verifyAgentDescriptor") {
        group = "verification"
        description = "Verifies this Tavall agent publishes one DI provider index, no first-party ServiceLoader descriptor, and one canonical ROLE.md."
        doLast {
            val providerFile = file("src/main/resources/$agentProviderIndex")
            check(providerFile.isFile) {
                "Missing Tavall agent provider index for $path: $providerFile"
            }
            val providers = providerFile.readLines()
                .map(String::trim)
                .filter(String::isNotBlank)
                .filterNot { it.startsWith("#") }
            check(providers.size == 1) {
                "Expected exactly one Tavall agent provider for $path, found ${providers.size}"
            }
            val legacyServiceFile = file("src/main/resources/$legacyAgentProviderService")
            check(!legacyServiceFile.exists()) {
                "First-party ServiceLoader agent composition is forbidden: $legacyServiceFile"
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

configure(runtimeModuleProjects.map(::project)) {
    val verifyRuntimeModuleDescriptor = tasks.register("verifyRuntimeModuleDescriptor") {
        group = "verification"
        description = "Verifies this runtime module publishes one DI provider index and no first-party ServiceLoader descriptor."
        doLast {
            val providerFile = file("src/main/resources/$moduleProviderIndex")
            check(providerFile.isFile) {
                "Missing Tavall AI runtime-module provider index for $path: $providerFile"
            }
            val providers = providerFile.readLines()
                .map(String::trim)
                .filter(String::isNotBlank)
                .filterNot { it.startsWith("#") }
            check(providers.size == 1) {
                "Expected exactly one Tavall AI runtime module provider for $path, found ${providers.size}"
            }
            val legacyServiceFile = file("src/main/resources/$legacyModuleProviderService")
            check(!legacyServiceFile.exists()) {
                "First-party ServiceLoader runtime-module composition is forbidden: $legacyServiceFile"
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
