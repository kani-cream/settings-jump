package com.github.kanicream.settingsjump.actions

import com.github.kanicream.settingsjump.navigation.SettingsJumpNotifier
import com.github.kanicream.settingsjump.navigation.SettingsNavigationService
import com.github.kanicream.settingsjump.state.SettingsJumpState
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/**
 * Fixed shortcut slot (design.md section 14). One class serves all ten actions;
 * the slot number comes from the stable action id ("SettingsJump.Slot3"), which
 * never changes when the slot mapping changes (Gate 5).
 */
class ShortcutSlotAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val slot = slotNumber() ?: return
        val assignment = SettingsJumpState.getInstance().slotAssignment(slot)
        if (assignment == null) {
            SettingsJumpNotifier.info(
                e.project,
                "Shortcut slot $slot is not assigned. Open Settings Jump and press " +
                    "the slot shortcut on a selected page to assign it.",
            )
            return
        }
        SettingsNavigationService.open(e.project, assignment.configurableId)
    }

    private fun slotNumber(): Int? {
        val id = ActionManager.getInstance().getId(this) ?: return null
        return id.removePrefix(ACTION_ID_PREFIX).toIntOrNull()
            ?.takeIf { it in 1..SettingsJumpState.SLOT_COUNT }
    }

    companion object {
        const val ACTION_ID_PREFIX = "SettingsJump.Slot"
    }
}
