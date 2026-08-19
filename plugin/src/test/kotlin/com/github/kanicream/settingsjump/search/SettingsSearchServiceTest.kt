package com.github.kanicream.settingsjump.search

import com.github.kanicream.settingsjump.model.AvailabilityKind
import com.github.kanicream.settingsjump.model.SettingsPage
import com.github.kanicream.settingsjump.model.SettingsScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchServiceTest {

    private fun page(
        id: String,
        name: String,
        path: List<String> = emptyList(),
        plugin: String? = null,
    ) = SettingsPage(
        id = id,
        displayName = name,
        parentId = null,
        path = path,
        scope = SettingsScope.APPLICATION,
        sourcePluginId = plugin,
        availability = AvailabilityKind.STATIC,
    )

    private val gradle = page(
        "reference.settingsdialog.project.gradle", "Gradle",
        path = listOf("Build, Execution, Deployment", "Build Tools"),
        plugin = "com.intellij.gradle",
    )
    private val gradleJvm = page("gradle.jvm", "Gradle JVM", path = listOf("Build Tools"))
    private val keymap = page("preferences.keymap", "Keymap")
    private val completion = page(
        "editor.preferences.completion", "コード補完",
        path = listOf("エディタ", "一般"),
    )

    private val all = listOf(gradleJvm, keymap, completion, gradle)

    @Test
    fun `exact display name outranks prefix match`() {
        val result = SettingsSearchService.search(all, "gradle", emptyList(), emptyList())
        assertEquals(listOf("Gradle", "Gradle JVM"), result.take(2).map { it.displayName })
    }

    @Test
    fun `normalization collapses case and whitespace`() {
        assertEquals("gradle jvm", SettingsSearchService.normalize("  GRADLE   Jvm "))
    }

    @Test
    fun `english query matches localized page via id tokens`() {
        val result = SettingsSearchService.search(all, "completion", emptyList(), emptyList())
        assertTrue(result.any { it.id == "editor.preferences.completion" })
    }

    @Test
    fun `path match works`() {
        val result = SettingsSearchService.search(all, "build tools", emptyList(), emptyList())
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { "Build Tools" in it.path })
    }

    @Test
    fun `favorite wins tie at equal rank`() {
        val a = page("a.page", "Copy Settings")
        val b = page("b.page", "Copy Options")
        val result = SettingsSearchService.search(
            listOf(a, b), "copy", listOf("b.page"), emptyList(),
        )
        assertEquals("b.page", result.first().id)
    }

    @Test
    fun `empty query lists favorites then recent then rest`() {
        val result = SettingsSearchService.search(
            all, "", listOf(keymap.id), listOf(gradle.id, keymap.id),
        )
        assertEquals(keymap.id, result[0].id)
        assertEquals(gradle.id, result[1].id)
        assertEquals(all.size, result.size)
    }

    @Test
    fun `no match returns empty`() {
        assertTrue(SettingsSearchService.search(all, "zzzzz", emptyList(), emptyList()).isEmpty())
    }
}
