package com.github.kanicream.settingsjump.actions

import com.github.kanicream.settingsjump.ui.SettingsJumpPopup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/** Entry point: opens the Settings Jump search popup for the current context. */
class OpenSettingsJumpAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        SettingsJumpPopup(e.project).show()
    }
}
