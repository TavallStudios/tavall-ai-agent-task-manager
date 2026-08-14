plugins {
    base
}

group = "org.tavall.ai"
extra["versionTagPrefix"] = "tavall-ai"
extra["fallbackVersion"] = "0.1.0"
apply(from = "gradle/git-version.gradle.kts")
version = extra["gitVersion"] as String

val junitVersion = "5.12.2"
val roleProviderService = "org.tavall.ai.agent.role.TavallAIAgentRoleProvider"
val moduleProviderService = "org.tavall.ai.bootstrap.TavallAIModuleProvider"
val roleProjects = listOf(
    "tavall-ai-agent-scheduler",
    "tavall-ai-agent-orchestration",
    "tavall-ai-agent-implementation",
    "tavall-ai-agent-review",
    "tavall-ai-agent-reconciliation",
    "tavall-ai-agent-e2e",
    "tavall-ai-agent-architecture",
    "tavall-ai-agent-documentation",
)
val moduleProjects = listOf(
    "tavall-ai-module-distributed-execution",
    "tavall-ai-module-builder",
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

configure(roleProjects.map(::project)) {
    dependencies {
        "api"(project(":tavall-ai-agent-core"))
    }

    val verifyRoleDescriptor = tasks.register("verifyRoleDescriptor") {
        group = "verification"
        description = "Verifies this Tavall AI role module publishes one provider and one canonical ROLE.md."
        doLast {
            val serviceFile = file("src/main/resources/META-INF/services/$roleProviderService")
            check(serviceFile.isFile) {
                "Missing ServiceLoader registration for $path: $serviceFile"
            }
            val providers = serviceFile.readLines()
                .map(String::trim)
                .filter(String::isNotBlank)
                .filterNot { it.startsWith("#") }
            check(providers.size == 1) {
                "Expected exactly one Tavall AI role provider for $path, found ${providers.size}"
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
        dependsOn(verifyRoleDescriptor)
    }
}

configure(moduleProjects.map(::project)) {
    val verifyModuleDescriptor = tasks.register("verifyModuleDescriptor") {
        group = "verification"
        description = "Verifies this Tavall AI capability/domain module publishes exactly one module provider."
        doLast {
            val serviceFile = file("src/main/resources/META-INF/services/$moduleProviderService")
            check(serviceFile.isFile) {
                "Missing Tavall AI module ServiceLoader registration for $path: $serviceFile"
            }
            val providers = serviceFile.readLines()
                .map(String::trim)
                .filter(String::isNotBlank)
                .filterNot { it.startsWith("#") }
            check(providers.size == 1) {
                "Expected exactly one Tavall AI module provider for $path, found ${providers.size}"
            }
        }
    }

    tasks.named("check") {
        dependsOn(verifyModuleDescriptor)
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
    description = "Runs checks for Tavall AI bootstrap, role modules, capability/domain modules, and runtimes."
    dependsOn(subprojects.map { it.tasks.named("check") })
}

tasks.named("check") {
    dependsOn(verifyTavallAISystem)
}
