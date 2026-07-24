import java.util.zip.ZipFile
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.Pmd

plugins {
    base
}

group = "org.tavall.ai"
extra["versionTagPrefix"] = "tavall-ai"
extra["fallbackVersion"] = "0.1.0"
apply(from = "gradle/git-version.gradle.kts")
version = extra["gitVersion"] as String

val springBootVersion = "4.0.5"
val mcpSdkVersion = "1.0.0"
val mongodbDriverVersion = "5.6.4"
val archunitVersion = "1.4.1"
val spoonVersion = "11.3.0"

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "checkstyle")
    apply(plugin = "pmd")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
        withSourcesJar()
    }

    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
        "testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    dependencyLocking {
        lockAllConfigurations()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 25
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("--enable-preview", "-parameters"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        jvmArgs(
            "--enable-preview",
            "-Dnet.bytebuddy.experimental=true",
            "-Dspring.test.context.cache.maxSize=1",
        )
        forkEvery = 0
        failFast = false
        maxHeapSize = "768m"
        maxParallelForks = 1
    }

    tasks.withType<JavaExec>().configureEach {
        jvmArgs("--enable-preview")
    }

    tasks.withType<Jar>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    extensions.configure<CheckstyleExtension> {
        toolVersion = "13.7.0"
        configFile = rootProject.file("config/lint/checkstyle.xml")
        isIgnoreFailures = true
        isShowViolations = false
    }

    extensions.configure<PmdExtension> {
        toolVersion = "7.18.0"
        ruleSetFiles = rootProject.files("config/lint/pmd-ruleset.xml")
        ruleSets = emptyList()
        isIgnoreFailures = true
        isConsoleOutput = false
    }

    tasks.withType<Checkstyle>().configureEach {
        reports {
            xml.required = true
            xml.outputLocation = layout.buildDirectory.file(
                "reports/checkstyle/${name}/checkstyle-result.xml",
            )
            html.required = false
            sarif.required = false
        }
    }

    tasks.withType<Pmd>().configureEach {
        reports {
            xml.required = true
            xml.outputLocation = layout.buildDirectory.file("reports/pmd/${name}/pmd.xml")
            html.required = false
        }
    }

    val verifyThinJar = tasks.register("verifyThinJar") {
        dependsOn(tasks.named("jar"))
        val archive = tasks.named<Jar>("jar").flatMap { it.archiveFile }
        inputs.file(archive)
        doLast {
            val forbidden = listOf(
                "com/fasterxml/",
                "io/modelcontextprotocol/",
                "org/springframework/",
                "org/postgresql/",
            )
            ZipFile(archive.get().asFile).use { jar ->
                val embedded = jar.entries().asSequence().map { it.name }
                    .firstOrNull { entry -> forbidden.any(entry::startsWith) }
                check(embedded == null) { "Third-party class embedded in thin JAR: $embedded" }
            }
        }
    }

    tasks.named("check") {
        dependsOn(verifyThinJar)
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
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

project(":tavall-ai-core") {
    dependencies {
        "api"("com.fasterxml.jackson.core:jackson-databind")
        "api"("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
        "api"("org.springframework.boot:spring-boot-starter-jdbc")
        "api"("org.springframework.boot:spring-boot-starter-validation")
        "api"("org.springframework.boot:spring-boot-starter-data-redis")
        "api"("org.postgresql:postgresql")
        "api"("io.zonky.test:embedded-postgres:2.1.0")
        "api"("org.mongodb:mongodb-driver-sync:$mongodbDriverVersion")
        "api"("org.tavall:gemini-api:0.0.1-SNAPSHOT")
        "api"("io.modelcontextprotocol.sdk:mcp-core:$mcpSdkVersion")
        "api"("io.modelcontextprotocol.sdk:mcp-json-jackson3:$mcpSdkVersion")
        "api"("com.tngtech.archunit:archunit-junit5-api:$archunitVersion")
        "api"("fr.inria.gforge.spoon:spoon-core:$spoonVersion") {
            exclude(group = "org.apache.maven")
            exclude(group = "org.apache.maven.shared")
        }
    }
}

val coreDependentProjects = listOf(
    "tavall-ai-artifact-tools",
    "tavall-ai-cache-tools",
    "tavall-ai-clean-java-harness",
    "tavall-ai-clean-java-mcp",
    "tavall-ai-computer-use-tools",
    "tavall-ai-context-tools",
    "tavall-ai-orchestration-tools",
    "tavall-ai-repo-tools",
    "tavall-ai-spring-webview",
    "tavall-ai-validation-tools",
    "tavall-ai-vector-memory-tools",
)

configure(coreDependentProjects.map(::project)) {
    dependencies {
        "api"(project(":tavall-ai-core"))
    }
}

project(":tavall-ai-spring-webview") {
    dependencies {
        "api"("org.springframework.boot:spring-boot-starter-security")
        "api"("org.springframework.boot:spring-boot-starter-web")
    }
    extensions.configure<PublishingExtension> {
        publications.named<MavenPublication>("mavenJava") {
            artifactId = "tavall-ai-mcp-http"
        }
    }
}

configure(listOf(project(":tavall-ai-clean-java-harness"), project(":tavall-ai-clean-java-mcp"))) {
    dependencies {
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    }
}

project(":tavall-ai-app") {
    apply(plugin = "application")
    extensions.configure<JavaApplication> {
        mainClass = "org.tavall.ai.app.AgentTaskManagerLauncher"
    }
    dependencies {
        "implementation"(project(":tavall-ai-core"))
        "implementation"(project(":tavall-ai-spring-webview"))
        "implementation"(project(":tavall-ai-clean-java-mcp"))
        "implementation"(project(":tavall-ai-clean-java-harness"))
        coreDependentProjects
            .filterNot { it in setOf("tavall-ai-clean-java-mcp", "tavall-ai-clean-java-harness", "tavall-ai-spring-webview") }
            .forEach { "implementation"(project(":$it")) }
        "testImplementation"("org.springframework.security:spring-security-test")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testImplementation"("org.springframework.boot:spring-boot-webmvc-test")
    }
    tasks.named<Jar>("jar") {
        archiveFileName = "tavall-ai-app.jar"
        manifest.attributes["Main-Class"] = "org.tavall.ai.app.AgentTaskManagerLauncher"
    }
}

project(":tavall-ai-clean-java-mcp") {
    apply(plugin = "application")
    extensions.configure<JavaApplication> {
        mainClass = "org.tavall.ai.app.cleanjava.CleanJavaMcpLauncher"
    }
    tasks.named<Jar>("jar") {
        archiveFileName = "tavall-ai-clean-java-mcp.jar"
        manifest.attributes["Main-Class"] = "org.tavall.ai.app.cleanjava.CleanJavaMcpLauncher"
    }
}

val stageDistribution = tasks.register<Sync>("stageDistribution") {
    val app = project(":tavall-ai-app")
    val cleanJava = project(":tavall-ai-clean-java-mcp")
    dependsOn(app.tasks.named("jar"), cleanJava.tasks.named("jar"))
    into(layout.projectDirectory.dir("distribution"))
    into("agent-task-manager") {
        from(app.tasks.named<Jar>("jar").flatMap { it.archiveFile }) {
            rename { "application.jar" }
        }
        into("libs") {
            from(app.configurations.getByName("runtimeClasspath"))
        }
    }
    into("clean-java-mcp") {
        from(cleanJava.tasks.named<Jar>("jar").flatMap { it.archiveFile }) {
            rename { "application.jar" }
        }
        into("libs") {
            from(cleanJava.configurations.getByName("runtimeClasspath"))
        }
    }
}

tasks.named("assemble") {
    dependsOn(stageDistribution)
}
