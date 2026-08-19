import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        // Baseline per plan/design.md section 20: IntelliJ 2024.2 / since-build 242.
        intellijIdeaCommunity("2024.2.5")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    // Settings Jump contributes no Configurable of its own, so there are no
    // searchable options to index; skipping saves a headless IDE launch.
    buildSearchableOptions = false
    pluginConfiguration {
        id = "com.github.kanicream.settingsjump"
        name = "Settings Jump"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "242"
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks.test {
    testLogging {
        showStandardStreams = true
    }
}
