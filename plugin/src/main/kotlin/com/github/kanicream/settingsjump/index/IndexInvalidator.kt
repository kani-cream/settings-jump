package com.github.kanicream.settingsjump.index

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.project.ProjectManager

/**
 * design.md 9.5: plugin load/unload only invalidates; the next popup open
 * rebuilds lazily. Never rebuilds eagerly on the event.
 */
internal class IndexInvalidator : DynamicPluginListener {

    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) = invalidateAll()

    override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) = invalidateAll()

    private fun invalidateAll() {
        AppSettingsPageIndex.getInstance().invalidate()
        ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed }
            .forEach { ProjectSettingsPageIndex.getInstance(it).invalidate() }
    }
}
