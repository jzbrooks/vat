import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
}

group = "com.jzbrooks"
version = "0.0.1"

repositories {
    mavenCentral()
    google()
}

val r8: Configuration by configurations.creating

dependencies {
    val osName = System.getProperty("os.name")
    val targetOs =
        when {
            osName == "Mac OS X" -> "macos"
            osName.startsWith("Win") -> "windows"
            osName.startsWith("Linux") -> "linux"
            else -> error("Unsupported OS: $osName")
        }

    val osArch = System.getProperty("os.arch")
    val targetArch =
        when (osArch) {
            "x86_64", "amd64" -> "x64"
            "aarch64" -> "arm64"
            else -> error("Unsupported arch: $osArch")
        }

    val target = "$targetOs-$targetArch"

    implementation("org.jetbrains.skiko:skiko:0.8.18")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-$target:0.8.18")
    implementation("com.jzbrooks:vgo:3.0.0")
    implementation("com.jzbrooks:vgo-core:3.0.0")

    r8("com.android.tools:r8:8.5.35")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

kotlin.sourceSets
    .getByName("main")
    .kotlin
    .srcDir("src/generated/kotlin")

tasks {
    test {
        useJUnitPlatform()
    }

    compileKotlin {
        dependsOn("generateConstants")
    }

    jar {
        dependsOn(configurations.runtimeClasspath)
        manifest {
            attributes["Main-Class"] = "com.jzbrooks.vat.MainKt"
            attributes["Bundle-Version"] = version
        }

        val sourceClasses =
            sourceSets.main
                .get()
                .output.classesDirs
        inputs.files(sourceClasses)
        destinationDirectory.set(layout.buildDirectory.dir("libs/debug"))

        doFirst {
            from(files(sourceClasses))
            from(
                configurations.runtimeClasspath
                    .get()
                    .asFileTree.files
                    .map(::zipTree),
            )

            exclude(
                "**/*.kotlin_metadata",
                "**/*.kotlin_module",
                "**/*.kotlin_builtins",
                "**/module-info.class",
                "META-INF/maven/**",
                "META-INF/*.version",
                "META-INF/LICENSE*",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/AL2.0",
                "META-INF/BCKEY.DSA",
                "META-INF/BC2048KE.DSA",
                "META-INF/BCKEY.SF",
                "META-INF/BC2048KE.SF",
                "**/NOTICE*",
                "javax/activation/**",
                "xsd/catalog.xml",
            )
        }
    }

    val optimize by registering(JavaExec::class) {
        description = "Runs r8 on the jar application."
        group = "build"

        inputs.file("build/libs/debug/vat-$version.jar")
        outputs.file("build/libs/vat.jar")

        val javaHome = System.getProperty("java.home")

        classpath(r8)
        mainClass = "com.android.tools.r8.R8"

        args(
            "--release",
            "--classfile",
            "--lib",
            javaHome,
            "--output",
            "build/libs/vat.jar",
            "--pg-conf",
            "optimize.pro",
            "build/libs/debug/vat-$version.jar",
        )

        dependsOn(getByName("jar"))
    }

    val binaryFileProp = layout.buildDirectory.file("libs/vat")
    val binary by registering {
        description = "Prepends shell script in the jar to improve CLI"
        group = "build"

        dependsOn(optimize)

        inputs.file("build/libs/vat.jar")
        outputs.file(binaryFileProp)

        doLast {
            val binaryFile = binaryFileProp.get().asFile
            binaryFile.parentFile.mkdirs()
            binaryFile.delete()
            binaryFile.appendText("#!/bin/sh\n\nexec java \$JAVA_OPTS -jar \$0 \"\$@\"\n\n")
            file("build/libs/vat.jar").inputStream().use { binaryFile.appendBytes(it.readBytes()) }
            binaryFile.setExecutable(true, false)
        }
    }

    val generateConstants by registering {
        finalizedBy("compileKotlin")

        outputs.files("$projectDir/src/generated/kotlin/com/jzbrooks/vat/BuildConstants.kt")

        doLast {
            val generatedDirectory = Paths.get("$projectDir/src/generated/kotlin/com/jzbrooks/vat")
            Files.createDirectories(generatedDirectory)
            val generatedFile = generatedDirectory.resolve("BuildConstants.kt")

            PrintWriter(generatedFile.toFile()).use { output ->
                val buildConstantsClass =
                    buildString {
                        appendLine(
                            """
                               |package com.jzbrooks.vat
                               |
                               |internal object BuildConstants {
                            """.trimMargin(),
                        )

                        val vgoProperties = mapOf("VERSION" to version)

                        for (property in vgoProperties) {
                            append("    const val ")
                            append(property.key.uppercase())
                            append(" = \"")
                            append(property.value)
                            appendLine('"')
                        }

                        appendLine("}")
                    }
                output.write(buildConstantsClass)
            }
        }
    }
}
