package com.github.kanicream.settingsjump.ui

import com.github.kanicream.settingsjump.actions.ShortcutSlotAction
import com.github.kanicream.settingsjump.state.SettingsJumpState

/** Pure text pieces for rendering shortcut-slot marks; keymap lookup stays in the renderer. */
internal object SlotMarkPresentation {

    private val CIRCLED_DIGITS = listOf("①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩")

    fun circledDigit(slot: Int): String {
        require(slot in 1..SettingsJumpState.SLOT_COUNT) {
            "slot must be 1..${SettingsJumpState.SLOT_COUNT}"
        }
        return CIRCLED_DIGITS[slot - 1]
    }

    fun fallbackLabel(slot: Int): String = "[Slot $slot]"

    fun slotActionId(slot: Int): String = "${ShortcutSlotAction.ACTION_ID_PREFIX}$slot"
}
