package com.github.kanicream.settingsjump.poc.analyze

import com.github.kanicream.settingsjump.poc.model.CollectedIndex
import com.github.kanicream.settingsjump.poc.model.PageRecord
import com.github.kanicream.settingsjump.poc.model.SettingsScope
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.project.Project

/** Collects application and (when a project is given) project Configurable EPs. */
object SettingsIndexCollector {

    fun collectAll(project: Project?): CollectedIndex {
        val start = System.nanoTime()
        val records = collectApplication() + (project?.let { collectProject(it) } ?: emptyList())
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000
        val (childRecords, childErrors) = probeChildrenEps(records)
        return CollectedIndex(records, elapsedMillis, childRecords, childErrors)
    }

    /**
     * Gate 2 probe: childrenEPName references another EP whose entries are themselves
     * static ConfigurableEP declarations. Checks whether they are enumerable from
     * metadata alone (feeds the v1.x decision in design.md 8.2).
     */
    private fun probeChildrenEps(
        records: List<PageRecord>,
    ): Pair<Map<String, List<PageRecord>>, Map<String, String>> {
        val results = mutableMapOf<String, List<PageRecord>>()
        val errors = mutableMapOf<String, String>()
        records.mapNotNull { it.childrenEpName }.distinct().forEach { epName ->
            try {
                val eps = ExtensionPointName.create<ConfigurableEP<Configurable>>(epName).extensionList
                results[epName] = eps.flatMap {
                    ConfigurableEpAnalyzer.analyze(it, SettingsScope.APPLICATION, depth = 1)
                }
            } catch (e: Exception) {
                errors[epName] = "${e.javaClass.simpleName}: ${e.message}"
            }
        }
        return results to errors
    }

    fun collectApplication(): List<PageRecord> =
        Configurable.APPLICATION_CONFIGURABLE.extensionList
            .flatMap { ConfigurableEpAnalyzer.analyze(it, SettingsScope.APPLICATION) }

    fun collectProject(project: Project): List<PageRecord> =
        Configurable.PROJECT_CONFIGURABLE.getExtensions(project)
            .flatMap { ConfigurableEpAnalyzer.analyze(it, SettingsScope.PROJECT) }
}
