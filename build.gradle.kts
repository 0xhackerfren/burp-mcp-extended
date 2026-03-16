import java.time.Instant
import java.net.URI

abstract class DownloadProxyJarTask : DefaultTask() {
    @get:Input
    abstract val proxyVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val libsDir = outputDir.get().asFile
        libsDir.mkdirs()
        val targetFile = File(libsDir, "mcp-proxy-all.jar")

        if (targetFile.exists()) {
            logger.lifecycle("Proxy JAR already exists at ${targetFile.absolutePath}, skipping download")
            return
        }

        val version = proxyVersion.get()
        val url = "https://github.com/PortSwigger/mcp-proxy/releases/download/v${version}/mcp-proxy-all.jar"
        logger.lifecycle("Downloading MCP proxy JAR from $url")

        URI(url).toURL().openStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        logger.lifecycle("Downloaded proxy JAR to ${targetFile.absolutePath} (${targetFile.length()} bytes)")
    }
}

abstract class EmbedProxyJarTask : DefaultTask() {
    @get:InputFile
    abstract val shadowJarFile: RegularFileProperty

    @get:InputDirectory
    abstract val projectDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun embedJar() {
        val shadowJar = shadowJarFile.get().asFile
        val libsDir = projectDir.dir("libs").get().asFile
        val proxyJarFile = File(libsDir, "mcp-proxy-all.jar")

        if (!proxyJarFile.exists()) {
            throw GradleException(
                "Proxy JAR not found at: ${proxyJarFile.absolutePath}\n" +
                "Run './gradlew downloadProxyJar' first, or use './gradlew shadowJar' for SSE-only builds."
            )
        }

        execOperations.exec {
            workingDir(projectDir.get().asFile)
            commandLine("jar", "uf", shadowJar.absolutePath, "-C", libsDir.absolutePath, proxyJarFile.name)
        }

        logger.lifecycle("Embedded proxy JAR into ${shadowJar.name}")
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    java
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()
description = providers.gradleProperty("description").get()

dependencies {
    compileOnly(libs.burp.montoya.api)

    implementation(libs.bundles.ktor.server)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mcp.kotlin.sdk)

    testImplementation(libs.bundles.test.framework)
    testImplementation(libs.bundles.ktor.test)
    testImplementation(libs.burp.montoya.api)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("java.toolchain.version").get().toInt()))
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("java.toolchain.version").get().toInt()))
    }

    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-Xjsr305=strict"
        )
    }
}

application {
    mainClass.set("net.portswigger.extension.ExtensionBase")
}

tasks {
    test {
        useJUnitPlatform()
        systemProperty("file.encoding", "UTF-8")

        testLogging {
            events("passed", "skipped", "failed")
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }

    jar {
        enabled = false
    }

    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()

        manifest {
            attributes(
                mapOf(
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to project.version,
                    "Implementation-Vendor" to "Suzu Labs (extended from PortSwigger)",
                    "Built-By" to "CI",
                    "Built-Date" to Instant.now().toString(),
                    "Built-JDK" to "${System.getProperty("java.version")} (${System.getProperty("java.vendor")} ${
                        System.getProperty("java.vm.version")
                    })",
                    "Created-By" to "Gradle ${gradle.gradleVersion}"
                )
            )
        }


        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/INDEX.LIST")
        exclude("META-INF/DEPENDENCIES")
        exclude("META-INF/NOTICE*")
        exclude("META-INF/LICENSE*")
        exclude("module-info.class")

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    register<DownloadProxyJarTask>("downloadProxyJar") {
        group = "build"
        description = "Downloads the MCP stdio proxy JAR from GitHub releases (required for Claude Desktop support)"
        proxyVersion.set("1.0.0")
        outputDir.set(layout.projectDirectory.dir("libs"))
    }

    register<EmbedProxyJarTask>("embedProxyJar") {
        group = "build"
        description = "Embeds the MCP proxy JAR into the shadow JAR for stdio support"
        dependsOn(shadowJar)
        shadowJarFile.set(shadowJar.flatMap { it.archiveFile })
        projectDir.set(layout.projectDirectory)
    }

    build {
        dependsOn(shadowJar)
    }

    withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

tasks.wrapper {
    gradleVersion = "9.2.0"
    distributionType = Wrapper.DistributionType.BIN
}