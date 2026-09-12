package com.github.kanicream.settingsjump.ui

import com.github.kanicream.settingsjump.state.SettingsJumpState
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import javax.swing.JList

internal class SettingsJumpListRenderer : ColoredListCellRenderer<SettingsJumpItem>() {

    override fun customizeCellRenderer(
        list: JList<out SettingsJumpItem>,
        value: SettingsJumpItem,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
        val state = SettingsJumpState.getInstance()
        when (value) {
            is SettingsJumpItem.Page -> {
                val page = value.page
                val slot = state.slotFor(page.id)
                appendMarks(slot, state.isFavorite(page.id), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                append(page.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                if (page.path.isNotEmpty()) {
                    append("  ${page.path.joinToString(" > ")}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                appendSlotShortcut(slot)
            }
            is SettingsJumpItem.MissingFavorite -> {
                val slot = state.slotFor(value.entry.configurableId)
                appendMarks(slot, favorite = true, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                append(value.entry.lastKnownDisplayName, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                append("  (unavailable)", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
                appendSlotShortcut(slot)
            }
        }
    }

    /** Leading mark column, slot before star; padding keeps names aligned across rows. */
    private fun appendMarks(slot: Int?, favorite: Boolean, attributes: SimpleTextAttributes) {
        if (slot != null) append("${SlotMarkPresentation.circledDigit(slot)} ", attributes)
        if (favorite) append("★ ", attributes)
        appendTextPadding(JBUI.scale(MARK_COLUMN_WIDTH))
    }

    private fun appendSlotShortcut(slot: Int?) {
        if (slot == null) return
        val keystroke = KeymapUtil.getFirstKeyboardShortcutText(SlotMarkPresentation.slotActionId(slot))
        val label = keystroke.ifEmpty { SlotMarkPresentation.fallbackLabel(slot) }
        append("  $label", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    companion object {
        // Wide enough for the widest mark pair "⑩ ★ " at default font size.
        private const val MARK_COLUMN_WIDTH = 40
    }
}
