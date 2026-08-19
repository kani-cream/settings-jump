package com.github.kanicream.settingsjump.poc.actions

import com.github.kanicream.settingsjump.poc.navigation.PocSettingsNavigator
import com.github.kanicream.settingsjump.poc.navigation.PocSettingsNavigator.NavigationResult
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/** Gate 3 condition 7: a missing ID must produce a notification only — no dialog, no IDE error. */
class OpenMissingSettingsAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        when (val result = PocSettingsNavigator.open(project, "settings.jump.poc.nonexistent.page")) {
            is NavigationResult.Rejected ->
                PocNotifications.info(project, "OK: fail closed as expected — ${result.reason}")
            is NavigationResult.Opened ->
                PocNotifications.warn(project, "UNEXPECTED: a dialog opened for a missing ID")
            is NavigationResult.Failed ->
                PocNotifications.warn(project, "UNEXPECTED: reached navigation despite preflight — ${result.reason}")
        }
    }
}
