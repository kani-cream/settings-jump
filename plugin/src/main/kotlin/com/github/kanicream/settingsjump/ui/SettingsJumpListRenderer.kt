package com.github.kanicream.settingsjump.ui

import com.github.kanicream.settingsjump.state.SettingsJumpState
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JList

internal class SettingsJumpListRenderer : ColoredListCellRenderer<SettingsJumpItem>() {

    override fun customizeCellRenderer(
        list: JList<out SettingsJumpItem>,
        value: SettingsJumpItem,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
        when (value) {
            is SettingsJumpItem.Page -> {
                val page = value.page
                if (SettingsJumpState.getInstance().isFavorite(page.id)) {
                    append("★ ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
                append(page.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                if (page.path.isNotEmpty()) {
                    append("  ${page.path.joinToString(" > ")}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
            is SettingsJumpItem.MissingFavorite -> {
                append("★ ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                append(value.entry.lastKnownDisplayName, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                append("  (unavailable)", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
            }
        }
    }
}
