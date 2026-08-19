package com.github.kanicream.settingsjump.poc.actions

import com.github.kanicream.settingsjump.poc.navigation.PocSettingsNavigator
import com.github.kanicream.settingsjump.poc.navigation.PocSettingsNavigator.NavigationResult
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

/** Gate 3: opens one Settings page from a stable ID via preflight + public predicate overload. */
class OpenSettingsByIdAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val targetId = Messages.showInputDialog(
            project,
            "Configurable ID to open (from the Gate 1/2 report):",
            "Settings Jump PoC — Gate 3",
            null,
            "preferences.lookFeel",
            null,
        )?.trim()
        if (targetId.isNullOrEmpty()) return

        when (val result = PocSettingsNavigator.open(project, targetId)) {
            is NavigationResult.Opened ->
                PocNotifications.info(
                    project,
                    "Opened $targetId (preflight ${result.preflightMillis} ms, open ${result.openMillis} ms)",
                )
            is NavigationResult.Rejected ->
                PocNotifications.warn(project, "Fail closed (preflight): ${result.reason}")
            is NavigationResult.Failed ->
                PocNotifications.warn(project, "Fail closed (navigation): ${result.reason}")
        }
    }
}
