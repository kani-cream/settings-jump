package com.github.kanicream.settingsjump.poc.analyze

import com.github.kanicream.settingsjump.poc.model.AvailabilityKind
import com.github.kanicream.settingsjump.poc.model.DisplayNameSource
import com.github.kanicream.settingsjump.poc.model.PageRecord
import com.github.kanicream.settingsjump.poc.model.SettingsScope
import com.intellij.openapi.options.ConfigurableEP

/**
 * Reads Configurable EP declarations into PageRecords using metadata only.
 * Must never instantiate a Configurable or a ConfigurableProvider (design.md 9.1).
 */
object ConfigurableEpAnalyzer {

    fun analyze(ep: ConfigurableEP<*>, scope: SettingsScope, depth: Int = 0): List<PageRecord> {
        val record = toRecord(ep, scope, depth)
        val nested = ep.children.orEmpty().flatMap { analyze(it, scope, depth + 1) }
        return listOf(record) + nested
    }

    // implementationClass is deprecated but still observed in the wild; reading it is the point.
    @Suppress("DEPRECATION")
    private fun toRecord(ep: ConfigurableEP<*>, scope: SettingsScope, depth: Int): PageRecord =
        PageRecord(
            id = ep.id?.takeIf { it.isNotBlank() },
            displayName = ep.displayName?.takeIf { it.isNotBlank() },
            key = ep.key?.takeIf { it.isNotBlank() },
            displayNameSource = displayNameSource(ep),
            parentId = ep.parentId?.takeIf { it.isNotBlank() },
            groupId = ep.groupId?.takeIf { it.isNotBlank() },
            scope = scope,
            sourcePluginId = ep.pluginDescriptor?.pluginId?.idString,
            availability = availabilityKind(ep),
            dynamic = ep.dynamic,
            childrenEpName = ep.childrenEPName?.takeIf { it.isNotBlank() },
            nestedDepth = depth,
            implementationRef = ep.instanceClass ?: ep.providerClass ?: ep.implementationClass,
        )

    /** design.md 4.1: explicit displayName, explicit bundle+key, or plugin default bundle+key. */
    private fun displayNameSource(ep: ConfigurableEP<*>): DisplayNameSource {
        val hasKey = !ep.key.isNullOrBlank()
        return when {
            !ep.displayName.isNullOrBlank() -> DisplayNameSource.EXPLICIT_DISPLAY_NAME
            hasKey && !ep.bundle.isNullOrBlank() -> DisplayNameSource.EXPLICIT_BUNDLE_KEY
            hasKey && ep.pluginDescriptor?.resourceBundleBaseName != null ->
                DisplayNameSource.PLUGIN_DEFAULT_BUNDLE_KEY
            else -> DisplayNameSource.NONE
        }
    }

    /** design.md 4.3: providerClass or nonDefaultProject means context-dependent presence. */
    private fun availabilityKind(ep: ConfigurableEP<*>): AvailabilityKind =
        if (ep.providerClass != null || ep.nonDefaultProject) AvailabilityKind.CONTEXTUAL
        else AvailabilityKind.STATIC
}
