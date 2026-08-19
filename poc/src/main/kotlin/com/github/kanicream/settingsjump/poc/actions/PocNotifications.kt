package com.github.kanicream.settingsjump.poc.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

internal object PocNotifications {

    fun info(project: Project?, message: String) = notify(project, message, NotificationType.INFORMATION)

    fun warn(project: Project?, message: String) = notify(project, message, NotificationType.WARNING)

    private fun notify(project: Project?, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Settings Jump PoC")
            .createNotification(message, type)
            .notify(project)
    }
}
