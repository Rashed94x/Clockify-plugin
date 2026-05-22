import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    // Target JVM 17 — the minimum JVM required by IntelliJ Platform 2024.1
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.2.6.2")
        bundledPlugin("Git4Idea")
        testFramework(TestFrameworkType.Platform)
    }
}

tasks {
    patchPluginXml {
        // Compatible with IntelliJ Platform 2024.1 (build 241) and all later versions
        sinceBuild.set("241")
        untilBuild.set(provider { null })
        changeNotes.set(provider {
            changelog.renderItem(
                changelog.getOrNull(project.version.toString())
                    ?: changelog.getLatest(),
                Changelog.OutputType.HTML
            )
        })
    }
}
