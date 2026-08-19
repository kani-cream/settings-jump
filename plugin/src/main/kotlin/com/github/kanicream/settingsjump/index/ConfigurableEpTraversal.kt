package com.github.kanicream.settingsjump.index

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project

/**
 * Shared metadata-only EP traversal, covering the three static child-supply
 * forms (design.md 8.2): explicit roots, nested <configurable>, and
 * childrenEPName-referenced EPs. Never instantiates a Configurable.
 */
internal object ConfigurableEpTraversal {

    private val log = logger<ConfigurableEpTraversal>()

    fun roots(project: Project?): List<ConfigurableEP<*>> =
        Configurable.APPLICATION_CONFIGURABLE.extensionList +
            (project?.let { Configurable.PROJECT_CONFIGURABLE.getExtensions(it) } ?: emptyList())

    /**
     * Enumerates the EP referenced by childrenEPName. Fail soft: an EP that is
     * not registered or not application-area yields no children, never an error.
     */
    fun childEps(epName: String): List<ConfigurableEP<*>> = try {
        ExtensionPointName.create<ConfigurableEP<Configurable>>(epName).extensionList
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: Exception) {
        log.debug("childrenEPName=$epName is not enumerable", e)
        emptyList()
    }

    /** Finds one EP by stable id across nested and childrenEPName-supplied children. */
    fun findById(project: Project?, targetId: String): ConfigurableEP<*>? =
        roots(project).firstNotNullOfOrNull { findInTree(it, targetId, mutableSetOf()) }

    private fun findInTree(
        ep: ConfigurableEP<*>,
        targetId: String,
        visitedChildEps: MutableSet<String>,
    ): ConfigurableEP<*>? {
        if (ep.id == targetId) return ep
        ep.children.orEmpty()
            .firstNotNullOfOrNull { findInTree(it, targetId, visitedChildEps) }
            ?.let { return it }
        val epName = ep.childrenEPName?.takeIf { it.isNotBlank() } ?: return null
        if (!visitedChildEps.add(epName)) return null
        return childEps(epName).firstNotNullOfOrNull { findInTree(it, targetId, visitedChildEps) }
    }
}
