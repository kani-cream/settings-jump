import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

// The distribution always comes from the baseline (plan/design.md section 20).
// -PideTarget=<name> rebuilds against a newer IDE from gradle.properties, only to run the
// tests there. Those builds use Java 25, which the platform requires from 2026.2 on, so
// their output must never be released: it would not load on the older IDEs we support.
val ideTargetName: String? = providers.gradleProperty("ideTarget").orNull

fun ideTargetProperty(target: String, key: String): String =
    providers.gradleProperty("ideTarget.$target.$key").orNull
        ?: error("Unknown ideTarget '$target': gradle.properties has no ideTarget.$target.$key")

val ideTargetVersion: String? = ideTargetName?.let { ideTargetProperty(it, "version") }

// CI splits Plugin Verifier into two jobs with -PverifierShard=older|unified. Splitting on a
// build number keeps the IDE list automatic: every new release lands in "unified". Without
// the property, verifyPlugin checks all IDEs.
val verifierShard: String? = providers.gradleProperty("verifierShard").orNull
val firstUnifiedBuild = 253

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(if (ideTargetName == null) 21 else 25)
    compilerOptions {
        // The oldest supported IDE bundles Kotlin stdlib 1.9.24. Without this pin the compiler
        // emits references to newer stdlib classes for coroutine code such as sequence {},
        // which fails on old IDEs at runtime with NoClassDefFoundError (SpillingKt). 2.0 is
        // the lowest value Kotlin 2.4 accepts, hence its deprecation warning.
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY)
    }
}

dependencies {
    intellijPlatform {
        // Baseline per plan/design.md section 20: IntelliJ 2024.2 / since-build 242.
        if (ideTargetVersion == null) intellijIdeaCommunity("2024.2.5") else intellijIdea(ideTargetVersion)
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
        changeNotes = """
            <b>1.1.1</b>
            <ul>
                <li>Confirmed working on IDE versions 2026.2 and 2026.3 RC, including
                the non-modal Settings window that is now the default.</li>
            </ul>
            <b>1.1.0</b>
            <ul>
                <li>Slot-assigned pages are now marked in the popup list with a
                circled slot number next to the favorite star, plus the bound
                keystroke (or [Slot n]) at the end of the row.</li>
                <li>The list refreshes immediately after assigning a slot.</li>
            </ul>
            <b>1.0.0</b>
            <ul>
                <li>Initial release: search settings pages and open them directly,
                favorites, recent pages, and ten keymap-bindable shortcut slots.</li>
            </ul>
        """.trimIndent()
        ideaVersion {
            sinceBuild = ideTargetName?.let { ideTargetProperty(it, "sinceBuild") } ?: "242"
        }
    }
    pluginVerification {
        ides {
            when (verifierShard) {
                null -> recommended()
                "older" -> select { untilBuild = "${firstUnifiedBuild - 1}.*" }
                "unified" -> select { sinceBuild = "$firstUnifiedBuild" }
                else -> error("Unknown verifierShard '$verifierShard': use older or unified")
            }
            // Released IDEs only come from the lists above, so add the upcoming one.
            if (verifierShard != "older") {
                create(IntelliJPlatformType.IntellijIdea, ideTargetProperty("next", "version"))
            }
        }
    }
}

tasks.test {
    testLogging {
        showStandardStreams = true
    }
}
