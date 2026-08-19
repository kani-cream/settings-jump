package com.github.kanicream.settingsjump.navigation

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

internal object SettingsJumpNotifier {

    private const val GROUP_ID = "Settings Jump"

    fun info(project: Project?, message: String) = notify(project, message, NotificationType.INFORMATION)

    fun warn(project: Project?, message: String) = notify(project, message, NotificationType.WARNING)

    private fun notify(project: Project?, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(message, type)
            .notify(project)
    }
}
