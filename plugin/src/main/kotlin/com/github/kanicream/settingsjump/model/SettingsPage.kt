package com.github.kanicream.settingsjump.model

/** design.md section 8. Values come from EP metadata only; no settings values are held. */
enum class SettingsScope { APPLICATION, PROJECT }

/**
 * design.md 4.3: UI hint only. Real availability is evaluated by navigation preflight,
 * for every page, at open time.
 */
enum class AvailabilityKind { STATIC, CONTEXTUAL }

/**
 * One eligible settings page (design.md section 4). Instances are immutable;
 * the index rebuilds a fresh list instead of mutating.
 */
data class SettingsPage(
    val id: String,
    val displayName: String,
    val parentId: String?,
    /** Ancestor display names, root first, excluding this page (design.md 8.2, fail-soft). */
    val path: List<String>,
    val scope: SettingsScope,
    val sourcePluginId: String?,
    val availability: AvailabilityKind,
) {
    val pathString: String
        get() = (path + displayName).joinToString(" > ")
}
