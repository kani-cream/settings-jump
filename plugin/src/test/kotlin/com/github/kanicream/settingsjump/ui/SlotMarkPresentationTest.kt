package com.github.kanicream.settingsjump.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SlotMarkPresentationTest {

    @Test
    fun `circled digit covers all ten slots`() {
        assertEquals("①", SlotMarkPresentation.circledDigit(1))
        assertEquals("②", SlotMarkPresentation.circledDigit(2))
        assertEquals("⑨", SlotMarkPresentation.circledDigit(9))
        assertEquals("⑩", SlotMarkPresentation.circledDigit(10))
    }

    @Test
    fun `circled digit rejects out of range slots`() {
        try {
            SlotMarkPresentation.circledDigit(0)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // only slots 1..10 exist
        }
        try {
            SlotMarkPresentation.circledDigit(11)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // only slots 1..10 exist
        }
    }

    @Test
    fun `fallback label names the slot number`() {
        assertEquals("[Slot 3]", SlotMarkPresentation.fallbackLabel(3))
        assertEquals("[Slot 10]", SlotMarkPresentation.fallbackLabel(10))
    }

    @Test
    fun `slot action id matches the registered action ids`() {
        assertEquals("SettingsJump.Slot4", SlotMarkPresentation.slotActionId(4))
    }
}
