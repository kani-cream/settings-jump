package com.github.kanicream.settingsjump.index

import com.github.kanicream.settingsjump.model.AvailabilityKind
import com.github.kanicream.settingsjump.model.SettingsScope
import com.intellij.DynamicBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ConfigurableEP

/** Raw eligible entry before path building. */
internal data class RawPage(
    val id: String,
    val displayName: String,
    val parentId: String?,
    val groupId: String?,
    val scope: SettingsScope,
    val sourcePluginId: String?,
    val availability: AvailabilityKind,
)

/**
 * Reads Configurable EP declarations using metadata only — never instantiates a
 * Configurable or a provider (design.md 9.1). Pages that fail the Eligible
 * conditions (design.md section 4) are skipped: no stable id, no resolvable
 * display name, or dynamic=true. Children supplied via nested <configurable>
 * and via childrenEPName-referenced EPs are both included (design.md 8.2).
 */
internal object ConfigurableMetadataReader {

    private val log = logger<ConfigurableMetadataReader>()

    fun read(
        ep: ConfigurableEP<*>,
        scope: SettingsScope,
        parentId: String? = null,
        visitedChildEps: MutableSet<String> = mutableSetOf(),
    ): List<RawPage> {
        if (ep.dynamic) return emptyList()
        val id = ep.id?.takeIf { it.isNotBlank() } ?: return emptyList()
        val displayName = resolveDisplayName(ep) ?: return emptyList()
        val page = RawPage(
            id = id,
            displayName = displayName,
            parentId = ep.parentId?.takeIf { it.isNotBlank() } ?: parentId,
            groupId = ep.groupId?.takeIf { it.isNotBlank() },
            scope = scope,
            sourcePluginId = ep.pluginDescriptor?.pluginId?.idString,
            availability = availabilityKind(ep),
        )
        val nested = ep.children.orEmpty().flatMap { read(it, scope, id, visitedChildEps) }
        val fromChildEp = childrenFromReferencedEp(ep, scope, id, visitedChildEps)
        return listOf(page) + nested + fromChildEp
    }

    private fun childrenFromReferencedEp(
        ep: ConfigurableEP<*>,
        scope: SettingsScope,
        parentId: String,
        visitedChildEps: MutableSet<String>,
    ): List<RawPage> {
        val epName = ep.childrenEPName?.takeIf { it.isNotBlank() } ?: return emptyList()
        if (!visitedChildEps.add(epName)) return emptyList()
        return ConfigurableEpTraversal.childEps(epName)
            .flatMap { read(it, scope, parentId, visitedChildEps) }
    }

    /**
     * design.md 4.1: explicit displayName, or key resolved against the explicit
     * bundle or the plugin descriptor's default resource bundle.
     */
    private fun resolveDisplayName(ep: ConfigurableEP<*>): String? {
        ep.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        val key = ep.key?.takeIf { it.isNotBlank() } ?: return null
        val descriptor = ep.pluginDescriptor ?: return null
        val bundleName = ep.bundle?.takeIf { it.isNotBlank() }
            ?: descriptor.resourceBundleBaseName ?: return null
        val loader = descriptor.classLoader ?: return null
        return try {
            DynamicBundle.getResourceBundle(loader, bundleName).getString(key)
        } catch (e: Exception) {
            log.debug("cannot resolve display name key=$key bundle=$bundleName", e)
            null
        }
    }

    /** design.md 4.3: provider or nonDefaultProject means context-dependent presence. */
    private fun availabilityKind(ep: ConfigurableEP<*>): AvailabilityKind =
        if (ep.providerClass != null || ep.nonDefaultProject) AvailabilityKind.CONTEXTUAL
        else AvailabilityKind.STATIC
}
