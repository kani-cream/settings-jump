package com.github.kanicream.settingsjump.state

import com.github.kanicream.settingsjump.model.SettingsPage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Application-level user state (design.md sections 12/13/14/15).
 * Persists identities (configurable ids) only — never settings values.
 * XML serialization requires mutable beans; all read accessors return copies.
 */
@Service(Service.Level.APP)
@State(name = "SettingsJump", storages = [Storage("settingsJump.xml")])
class SettingsJumpState : PersistentStateComponent<SettingsJumpState.Model> {

    class Model {
        var schemaVersion: Int = CURRENT_SCHEMA_VERSION
        var favorites: MutableList<FavoriteEntry> = mutableListOf()
        var recent: MutableList<RecentEntry> = mutableListOf()
        var slots: MutableList<SlotEntry> = mutableListOf()
    }

    class FavoriteEntry {
        var configurableId: String = ""
        var scope: String = ""
        var lastKnownDisplayName: String = ""
        var lastKnownPath: String = ""
    }

    class RecentEntry {
        var configurableId: String = ""
        var scope: String = ""
        var lastKnownDisplayName: String = ""
        var lastKnownPath: String = ""
    }

    class SlotEntry {
        var slot: Int = 0
        var configurableId: String = ""
        var lastKnownDisplayName: String = ""
    }

    private var model = Model()

    override fun getState(): Model = model

    override fun loadState(state: Model) {
        // Unknown future schema: keep the data untouched rather than migrating blindly
        // (design.md 15.1 — never destroy what a newer version wrote).
        if (state.schemaVersion > CURRENT_SCHEMA_VERSION) return
        XmlSerializerUtil.copyBean(state, model)
        model.schemaVersion = CURRENT_SCHEMA_VERSION
    }

    // --- Favorites -----------------------------------------------------------

    fun favorites(): List<FavoriteEntry> = model.favorites.toList()

    fun isFavorite(configurableId: String): Boolean =
        model.favorites.any { it.configurableId == configurableId }

    /** Returns true when the page is a favorite after the call. */
    fun toggleFavorite(page: SettingsPage): Boolean {
        val existing = model.favorites.filter { it.configurableId == page.id }
        return if (existing.isNotEmpty()) {
            model.favorites.removeAll(existing)
            false
        } else {
            model.favorites.add(FavoriteEntry().apply {
                configurableId = page.id
                scope = page.scope.name
                lastKnownDisplayName = page.displayName
                lastKnownPath = page.pathString
            })
            true
        }
    }

    fun removeFavorite(configurableId: String) {
        model.favorites.removeAll { it.configurableId == configurableId }
    }

    // --- Recent --------------------------------------------------------------

    fun recent(): List<RecentEntry> = model.recent.toList()

    fun recordRecent(page: SettingsPage) {
        model.recent.removeAll { it.configurableId == page.id }
        model.recent.add(0, RecentEntry().apply {
            configurableId = page.id
            scope = page.scope.name
            lastKnownDisplayName = page.displayName
            lastKnownPath = page.pathString
        })
        while (model.recent.size > MAX_RECENT) {
            model.recent.removeAt(model.recent.size - 1)
        }
    }

    // --- Shortcut slots ------------------------------------------------------

    fun slotAssignment(slot: Int): SlotEntry? =
        model.slots.firstOrNull { it.slot == slot }

    /** Assigning the already-assigned page clears the slot. Returns the new entry, or null when cleared. */
    fun assignSlot(slot: Int, page: SettingsPage): SlotEntry? {
        require(slot in 1..SLOT_COUNT) { "slot must be 1..$SLOT_COUNT" }
        val current = slotAssignment(slot)
        model.slots.removeAll { it.slot == slot }
        if (current?.configurableId == page.id) return null
        val entry = SlotEntry().apply {
            this.slot = slot
            configurableId = page.id
            lastKnownDisplayName = page.displayName
        }
        model.slots.add(entry)
        return entry
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_RECENT = 20
        const val SLOT_COUNT = 10

        fun getInstance(): SettingsJumpState = ApplicationManager.getApplication().service()
    }
}
