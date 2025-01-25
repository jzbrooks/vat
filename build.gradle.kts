import org.gradle.internal.extensions.stdlib.capitalized
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.changelog)
    alias(libs.plugins.ktlint)
}

group = "com.jzbrooks"
version = "0.0.4"

repositories {
    mavenCentral()
    google()
}

val r8: Configuration by configurations.creating

val targets = mapOf(
    "macos" to listOf("arm64"),
    "windows" to listOf("x64", "arm64"),
    "linux" to listOf("x64", "arm64"),
)

val macosArm64RuntimeOnly: Configuration by configurations.creating {
    extendsFrom(configurations["runtimeOnly"])
}
val windowsX64RuntimeOnly: Configuration by configurations.creating {
    extendsFrom(configurations["runtimeOnly"])
}
val windowsArm64RuntimeOnly: Configuration by configurations.creating {
    extendsFrom(configurations["runtimeOnly"])
}
val linuxX64RuntimeOnly: Configuration by configurations.creating {
    extendsFrom(configurations["runtimeOnly"])
}
val linuxArm64RuntimeOnly: Configuration by configurations.creating {
    extendsFrom(configurations["runtimeOnly"])
}

dependencies {
    implementation(libs.skiko)

    implementation(libs.vgo)
    implementation(libs.vgoCore)

    macosArm64RuntimeOnly(libs.skikoMacArm)
    linuxArm64RuntimeOnly(libs.skikoLinuxArm)
    linuxX64RuntimeOnly(libs.skikoLinuxIntel)
    windowsArm64RuntimeOnly(libs.skikoWindowsArm)
    windowsX64RuntimeOnly(libs.skikoWindowsIntel)

    r8(libs.r8)

    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
}

sourceSets {
    main {
        kotlin.srcDir("src/generated/kotlin")
    }
}

tasks {
    test {
        useJUnitPlatform()
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

                        val vatProperties = mapOf("VERSION" to version)

                        for (property in vatProperties) {
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

    named("runKtlintCheckOverMainSourceSet") {
        dependsOn(generateConstants)
    }

    named("runKtlintFormatOverMainSourceSet") {
        dependsOn(generateConstants)
    }

    compileKotlin {
        dependsOn(generateConstants)
    }

    for ((os, architectures) in targets) {
        for (arch in architectures) {
            val target = "$os${arch.capitalized()}"

            register<Jar>("${target}Jar") {
                manifest {
                    attributes["Main-Class"] = "com.jzbrooks.vat.MainKt"
                    attributes["Bundle-Version"] = version
                }

                archiveBaseName.set("vat-$os-$arch")
                destinationDirectory.set(layout.buildDirectory.dir("libs/debug"))

                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                val sourceClasses = sourceSets.main.get().output.classesDirs

                inputs.files(sourceClasses)

                from(sourceClasses.files)
                from(configurations["${target}RuntimeOnly"].asFileTree.files.map(::zipTree))
                from(configurations.runtimeClasspath.get().asFileTree.files.map(::zipTree))

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

            register<JavaExec>("${target}Optimize") {
                description = "Runs r8 on the jar application."
                group = "build"

                inputs.file("build/libs/debug/vat-$os-$arch-$version.jar")
                outputs.file("build/libs/vat-$os-$arch-$version.jar")

                val javaHome = System.getProperty("java.home")

                classpath(r8)
                mainClass = "com.android.tools.r8.R8"

                args(
                    "--release",
                    "--classfile",
                    "--lib",
                    javaHome,
                    "--output",
                    "build/libs/vat-$os-$arch.jar",
                    "--pg-conf",
                    "optimize.pro",
                    "build/libs/debug/vat-$os-$arch-$version.jar",
                )

                dependsOn("${target}Jar")
            }

            if (os != "windows") {
                val binaryFileProp = layout.buildDirectory.file("libs/vat-$os-$arch")
                register("${target}Binary") {
                    description = "Prepends shell script in the jar to improve CLI"
                    group = "build"

                    dependsOn("${target}Optimize")

                    inputs.file("build/libs/vat-$os-$arch.jar")
                    outputs.file(binaryFileProp)

                    doLast {
                        val binaryFile = binaryFileProp.get().asFile
                        binaryFile.parentFile.mkdirs()
                        binaryFile.delete()
                        binaryFile.appendText("#!/bin/sh\n\nexec java \$JAVA_OPTS -jar \$0 \"\$@\"\n\n")
                        file("build/libs/vat-$os-$arch.jar").inputStream()
                            .use { binaryFile.appendBytes(it.readBytes()) }
                        binaryFile.setExecutable(true, false)
                    }
                }
            }
        }
    }

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

    jar {
        description = "Runs r8 on the jar application for the current system ($targetOs-$targetArch)."
        group = "build"
        dependsOn("$targetOs${targetArch.capitalized()}Jar")
    }

    val optimize by registering {
        description = "Runs r8 on the jar application for the current system ($targetOs-$targetArch)."
        group = "build"
        dependsOn("$targetOs${targetArch.capitalized()}Optimize")
    }

    if (targetOs != "windows") {
        val binary by registering {
            description =
                "Prepends shell script in the jar to improve CLI for the current system ($targetOs-$targetArch)"
            group = "build"
            dependsOn("$targetOs${targetArch.capitalized()}Binary")
        }
    }
}
