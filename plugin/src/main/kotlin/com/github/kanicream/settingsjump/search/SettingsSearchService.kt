package com.github.kanicream.settingsjump.search

import com.github.kanicream.settingsjump.model.SettingsPage

/**
 * Pure search over the page list (design.md section 11). Stateless and free of
 * platform types so ranking is unit-testable.
 */
object SettingsSearchService {

    /**
     * Empty query: favorites first (their saved order), then recent (most recent
     * first), then everything else alphabetically (design.md 7.2).
     */
    fun search(
        pages: List<SettingsPage>,
        query: String,
        favoriteIds: List<String>,
        recentIds: List<String>,
    ): List<SettingsPage> {
        val normalized = normalize(query)
        if (normalized.isEmpty()) return emptyQueryOrder(pages, favoriteIds, recentIds)

        val favoriteSet = favoriteIds.toSet()
        val recentRank = recentIds.withIndex().associate { (i, id) -> id to i }
        return pages
            .mapNotNull { page ->
                val rank = rank(page, normalized) ?: return@mapNotNull null
                Triple(page, rank, tieBreaker(page, favoriteSet, recentRank))
            }
            .sortedWith(
                compareBy({ it.second }, { it.third }, { it.first.displayName.lowercase() }),
            )
            .map { it.first }
    }

    /** design.md 11.2: case-insensitive, trimmed, collapsed whitespace. */
    fun normalize(query: String): String =
        query.trim().lowercase().replace(Regex("\\s+"), " ")

    /** design.md 11.1: tokens from display name, path, id, plugin id. */
    fun searchTokens(page: SettingsPage): Set<String> = buildSet {
        addAll(wordsOf(page.displayName))
        page.path.forEach { addAll(wordsOf(it)) }
        addAll(idTokens(page.id))
        page.sourcePluginId?.let { addAll(idTokens(it)) }
    }

    // Lower is better; null means no match (design.md 11.3).
    private fun rank(page: SettingsPage, query: String): Int? {
        val name = page.displayName.lowercase()
        val queryTokens = query.split(' ')
        val tokens = searchTokens(page)
        return when {
            name == query -> 0
            name.startsWith(query) -> 1
            queryTokens.all { q -> wordsOf(page.displayName).any { it.startsWith(q) } } -> 2
            queryTokens.all { q -> tokens.any { it.startsWith(q) } } -> 3
            page.pathString.lowercase().contains(query) -> 4
            queryTokens.all { q -> name.contains(q) || page.pathString.lowercase().contains(q) } -> 5
            else -> null
        }
    }

    private fun tieBreaker(
        page: SettingsPage,
        favoriteIds: Set<String>,
        recentRank: Map<String, Int>,
    ): Int {
        if (page.id in favoriteIds) return 0
        recentRank[page.id]?.let { return 1 + it }
        return 1000
    }

    private fun emptyQueryOrder(
        pages: List<SettingsPage>,
        favoriteIds: List<String>,
        recentIds: List<String>,
    ): List<SettingsPage> {
        val byId = pages.associateBy { it.id }
        val favoriteSet = favoriteIds.toSet()
        val favorites = favoriteIds.mapNotNull { byId[it] }
        val recents = recentIds.mapNotNull { byId[it] }.filter { it.id !in favoriteSet }
        val listed = (favorites + recents).map { it.id }.toSet()
        val rest = pages.filter { it.id !in listed }.sortedBy { it.displayName.lowercase() }
        return favorites + recents + rest
    }

    private fun wordsOf(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private fun idTokens(id: String): List<String> =
        id.lowercase().split(Regex("[._\\-]+")).filter { it.isNotEmpty() } + id.lowercase()
}
