package com.github.kanicream.settingsjump.index

import com.github.kanicream.settingsjump.model.SettingsScope
import com.github.kanicream.settingsjump.navigation.SettingsNavigationService
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Integration layer per design.md 22.2: EP discovery, scope, id resolution, fail closed. */
class IndexAndNavigationIntegrationTest : BasePlatformTestCase() {

    fun testDiscoveryFindsEligiblePagesInBothScopes() {
        val pages = SettingsPageIndexFacade.pagesForContext(project)
        assertTrue("expected eligible pages", pages.isNotEmpty())
        assertTrue(pages.any { it.scope == SettingsScope.APPLICATION })
        assertTrue(pages.any { it.scope == SettingsScope.PROJECT })
    }

    fun testDisplayNamesAreResolvedNotKeys() {
        val pages = SettingsPageIndexFacade.pagesForContext(project)
        val keymap = pages.firstOrNull { it.id == "preferences.keymap" }
        assertNotNull("keymap page expected", keymap)
        // A resolved localized label, not the raw bundle key "keymap.display.name".
        assertFalse(keymap!!.displayName.contains(".display.name"))
    }

    fun testFindByIdResolvesAndMissingIdDoesNot() {
        val pages = SettingsPageIndexFacade.pagesForContext(project)
        val some = pages.first()
        assertEquals(some.id, SettingsPageIndexFacade.findById(project, some.id)?.id)
        assertNull(SettingsPageIndexFacade.findById(project, "settings.jump.missing.page"))
    }

    fun testNavigationFailsClosedForMissingId() {
        val outcome = SettingsNavigationService.open(project, "settings.jump.missing.page")
        assertTrue(outcome is SettingsNavigationService.Outcome.NotOpened)
    }

    fun testChildrenEpNamePagesAreIndexed() {
        // Code Completion is supplied via preferences.editor's
        // childrenEPName=com.intellij.editorOptionsProvider (design.md 8.2 / Phase 0 Finding B).
        val pages = SettingsPageIndexFacade.pagesForContext(project)
        val completion = pages.firstOrNull { it.id == "editor.preferences.completion" }
        assertNotNull("Code Completion must be indexed via childrenEPName", completion)
        assertEquals("preferences.editor", completion!!.parentId)
        assertTrue("parent display name should be on the path", completion.path.isNotEmpty())
        // The declaring page itself stays eligible too.
        assertNotNull(pages.firstOrNull { it.id == "preferences.editor" })
    }

    fun testChildrenEpNamePageIsNavigable() {
        // The EP lookup used by navigation must also traverse childrenEPName children.
        assertNotNull(ConfigurableEpTraversal.findById(project, "editor.preferences.completion"))
    }

    fun testInvalidateRebuildsIndex() {
        val app = AppSettingsPageIndex.getInstance()
        val before = app.pages()
        app.invalidate()
        val after = app.pages()
        assertEquals(before.map { it.id }, after.map { it.id })
    }
}
