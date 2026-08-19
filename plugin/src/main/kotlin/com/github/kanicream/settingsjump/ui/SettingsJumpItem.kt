package com.github.kanicream.settingsjump.ui

import com.github.kanicream.settingsjump.model.SettingsPage
import com.github.kanicream.settingsjump.state.SettingsJumpState

/** A row in the popup list. Favorites whose page vanished stay listed but unavailable (design.md 21.1). */
internal sealed interface SettingsJumpItem {

    data class Page(val page: SettingsPage) : SettingsJumpItem

    data class MissingFavorite(val entry: SettingsJumpState.FavoriteEntry) : SettingsJumpItem
}
