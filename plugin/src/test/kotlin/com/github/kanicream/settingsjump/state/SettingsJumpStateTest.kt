package com.github.kanicream.settingsjump.state

import com.github.kanicream.settingsjump.model.AvailabilityKind
import com.github.kanicream.settingsjump.model.SettingsPage
import com.github.kanicream.settingsjump.model.SettingsScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsJumpStateTest {

    private fun page(id: String, name: String = id) = SettingsPage(
        id = id,
        displayName = name,
        parentId = null,
        path = listOf("Tools"),
        scope = SettingsScope.APPLICATION,
        sourcePluginId = null,
        availability = AvailabilityKind.STATIC,
    )

    @Test
    fun `favorite toggle adds then removes`() {
        val state = SettingsJumpState()
        assertTrue(state.toggleFavorite(page("a")))
        assertTrue(state.isFavorite("a"))
        assertFalse(state.toggleFavorite(page("a")))
        assertFalse(state.isFavorite("a"))
    }

    @Test
    fun `favorite keeps last known display metadata`() {
        val state = SettingsJumpState()
        state.toggleFavorite(page("a", "Gradle"))
        val entry = state.favorites().single()
        assertEquals("Gradle", entry.lastKnownDisplayName)
        assertEquals("Tools > Gradle", entry.lastKnownPath)
    }

    @Test
    fun `recent moves duplicate to front and caps at 20`() {
        val state = SettingsJumpState()
        (1..25).forEach { state.recordRecent(page("p$it")) }
        state.recordRecent(page("p10"))
        val recent = state.recent()
        assertEquals(SettingsJumpState.MAX_RECENT, recent.size)
        assertEquals("p10", recent.first().configurableId)
        assertEquals(1, recent.count { it.configurableId == "p10" })
    }

    @Test
    fun `slot assign overwrite and clear by reassign`() {
        val state = SettingsJumpState()
        assertNotNull(state.assignSlot(1, page("a")))
        assertEquals("a", state.slotAssignment(1)?.configurableId)
        assertNotNull(state.assignSlot(1, page("b")))
        assertEquals("b", state.slotAssignment(1)?.configurableId)
        assertNull(state.assignSlot(1, page("b")))
        assertNull(state.slotAssignment(1))
    }

    @Test
    fun `slot number is validated`() {
        val state = SettingsJumpState()
        try {
            state.assignSlot(11, page("a"))
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // fail fast per design: only slots 1..10 exist
        }
    }

    @Test
    fun `newer schema is left untouched`() {
        val state = SettingsJumpState()
        state.toggleFavorite(page("mine"))
        val future = SettingsJumpState.Model().apply {
            schemaVersion = SettingsJumpState.CURRENT_SCHEMA_VERSION + 1
        }
        state.loadState(future)
        assertTrue(state.isFavorite("mine"))
    }

    @Test
    fun `load state copies favorites recent slots`() {
        val donor = SettingsJumpState()
        donor.toggleFavorite(page("f"))
        donor.recordRecent(page("r"))
        donor.assignSlot(3, page("s"))

        val state = SettingsJumpState()
        state.loadState(donor.state)
        assertTrue(state.isFavorite("f"))
        assertEquals("r", state.recent().single().configurableId)
        assertEquals("s", state.slotAssignment(3)?.configurableId)
    }
}
