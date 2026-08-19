package com.github.kanicream.settingsjump.index

import com.github.kanicream.settingsjump.model.SettingsPage

/**
 * Builds navigation paths from resolvable public metadata (design.md 8.2).
 * This is deliberately NOT a reproduction of the Settings UI tree: parents are
 * resolved via explicit parentId / nested declaration; an unresolvable parent
 * or group id degrades to a humanized label instead of failing.
 */
internal object PathBuilder {

    private const val MAX_DEPTH = 10

    fun build(rawPages: List<RawPage>): List<SettingsPage> {
        val byId = rawPages.associateBy { it.id }
        return rawPages.map { raw ->
            SettingsPage(
                id = raw.id,
                displayName = raw.displayName,
                parentId = raw.parentId,
                path = ancestorNames(raw, byId),
                scope = raw.scope,
                sourcePluginId = raw.sourcePluginId,
                availability = raw.availability,
            )
        }
    }

    private fun ancestorNames(raw: RawPage, byId: Map<String, RawPage>): List<String> {
        val names = ArrayDeque<String>()
        var current: RawPage = raw
        var depth = 0
        while (depth++ < MAX_DEPTH) {
            val parentId = current.parentId ?: break
            val parent = byId[parentId]
            if (parent == null) {
                names.addFirst(humanize(parentId))
                break
            }
            names.addFirst(parent.displayName)
            current = parent
        }
        if (names.isEmpty() && raw.parentId == null) {
            raw.groupId?.takeIf { it != "root" }?.let { names.addFirst(humanize(it)) }
        }
        return names.toList()
    }

    /** "build.tools" -> "Build Tools"; a display fallback, never an identity. */
    private fun humanize(id: String): String =
        id.substringAfterLast('.').replace('_', ' ')
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replaceFirstChar { it.uppercase() }
            .let { leaf ->
                val prefix = id.substringBeforeLast('.', "")
                if (prefix.isEmpty()) leaf
                else "${prefix.substringAfterLast('.').replaceFirstChar { it.uppercase() }} $leaf"
            }
}
