package com.github.kanicream.settingsjump.poc.model

/** Mirrors design.md section 8. PoC adds raw observation fields for Gate 1/2 measurement. */
enum class SettingsScope { APPLICATION, PROJECT }

/** design.md 4.3: UI hint only. Real availability is evaluated at navigation preflight. */
enum class AvailabilityKind { STATIC, CONTEXTUAL }

/** How the localized display name can be resolved from EP metadata alone (design.md 4.1). */
enum class DisplayNameSource {
    EXPLICIT_DISPLAY_NAME,
    EXPLICIT_BUNDLE_KEY,
    PLUGIN_DEFAULT_BUNDLE_KEY,
    NONE,
}

/** Page-level reasons a page fails the Eligible conditions (design.md section 4). */
enum class IneligibleReason {
    NO_STABLE_ID,
    NO_DISPLAY_METADATA,
    DYNAMIC_CHILDREN,
    CHILDREN_EP_NAME,
}

/**
 * One observed Configurable EP declaration. Values come from EP metadata only;
 * no Configurable / provider instance is created during collection (design.md 9.1).
 */
data class PageRecord(
    val id: String?,
    val displayName: String?,
    val key: String?,
    val displayNameSource: DisplayNameSource,
    val parentId: String?,
    val groupId: String?,
    val scope: SettingsScope,
    val sourcePluginId: String?,
    val availability: AvailabilityKind,
    val dynamic: Boolean,
    val childrenEpName: String?,
    val nestedDepth: Int,
    val implementationRef: String?,
) {
    val declaresProviderOrNonDefault: Boolean
        get() = availability == AvailabilityKind.CONTEXTUAL

    fun ineligibleReasons(): List<IneligibleReason> = buildList {
        if (id == null) add(IneligibleReason.NO_STABLE_ID)
        if (displayNameSource == DisplayNameSource.NONE) add(IneligibleReason.NO_DISPLAY_METADATA)
        if (dynamic) add(IneligibleReason.DYNAMIC_CHILDREN)
        if (childrenEpName != null) add(IneligibleReason.CHILDREN_EP_NAME)
    }

    val isEligible: Boolean
        get() = ineligibleReasons().isEmpty()
}

/** Result of one collection pass, with timing for the Gate 3 performance observation. */
data class CollectedIndex(
    val records: List<PageRecord>,
    val collectionMillis: Long,
    /** Gate 2 probe: pages supplied via childrenEPName, grouped by the referenced EP name. */
    val childrenEpRecords: Map<String, List<PageRecord>> = emptyMap(),
    /** Gate 2 probe: childrenEPName EPs that could not be enumerated, with the failure reason. */
    val childrenEpProbeErrors: Map<String, String> = emptyMap(),
)
