package com.github.kanicream.settingsjump.index

import com.github.kanicream.settingsjump.model.SettingsPage
import com.github.kanicream.settingsjump.model.SettingsScope
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project

/**
 * Two-layer index (design.md 9.2): the application layer is shared by all
 * windows; each project has its own layer with project lifecycle. Both are
 * invalidated by plugin load/unload and rebuilt lazily on next access.
 */
@Service(Service.Level.APP)
class AppSettingsPageIndex {

    @Volatile
    private var cache: List<SettingsPage>? = null

    fun pages(): List<SettingsPage> =
        cache ?: buildPages().also { cache = it }

    fun invalidate() {
        cache = null
    }

    private fun buildPages(): List<SettingsPage> {
        val raw = Configurable.APPLICATION_CONFIGURABLE.extensionList
            .flatMap { ConfigurableMetadataReader.read(it, SettingsScope.APPLICATION) }
        return PathBuilder.build(raw)
    }

    companion object {
        fun getInstance(): AppSettingsPageIndex = ApplicationManager.getApplication().service()
    }
}

@Service(Service.Level.PROJECT)
class ProjectSettingsPageIndex(private val project: Project) {

    @Volatile
    private var cache: List<SettingsPage>? = null

    fun pages(): List<SettingsPage> =
        cache ?: buildPages().also { cache = it }

    fun invalidate() {
        cache = null
    }

    private fun buildPages(): List<SettingsPage> {
        val raw = Configurable.PROJECT_CONFIGURABLE.getExtensions(project)
            .flatMap { ConfigurableMetadataReader.read(it, SettingsScope.PROJECT) }
        return PathBuilder.build(raw)
    }

    companion object {
        fun getInstance(project: Project): ProjectSettingsPageIndex = project.service()
    }
}

/** Combined view for the current context (design.md 9.2: forContext). */
object SettingsPageIndexFacade {

    fun pagesForContext(project: Project?): List<SettingsPage> {
        val app = AppSettingsPageIndex.getInstance().pages()
        val proj = project
            ?.takeIf { !it.isDisposed }
            ?.let { ProjectSettingsPageIndex.getInstance(it).pages() }
            ?: emptyList()
        return app + proj
    }

    fun findById(project: Project?, id: String): SettingsPage? =
        pagesForContext(project).firstOrNull { it.id == id }
}
