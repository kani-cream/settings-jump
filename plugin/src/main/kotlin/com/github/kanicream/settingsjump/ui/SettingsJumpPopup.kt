package com.github.kanicream.settingsjump.ui

import com.github.kanicream.settingsjump.index.SettingsPageIndexFacade
import com.github.kanicream.settingsjump.navigation.SettingsJumpNotifier
import com.github.kanicream.settingsjump.navigation.SettingsNavigationService
import com.github.kanicream.settingsjump.search.SettingsSearchService
import com.github.kanicream.settingsjump.state.SettingsJumpState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

/**
 * Keyboard-first search popup (design.md section 7).
 * Enter opens, Shift+Enter toggles favorite, Ctrl/Cmd+1..0 assigns a shortcut slot.
 */
internal class SettingsJumpPopup(private val project: Project?) {

    private val searchField = SearchTextField(false)
    private val listModel = CollectionListModel<SettingsJumpItem>()
    private val list = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = SettingsJumpListRenderer()
        setEmptyText("No matching settings pages")
    }
    private var popup: JBPopup? = null

    fun show() {
        refresh()
        val panel = JPanel(BorderLayout()).apply {
            add(searchField, BorderLayout.NORTH)
            add(JBScrollPane(list), BorderLayout.CENTER)
            add(hintLabel(), BorderLayout.SOUTH)
            preferredSize = Dimension(JBUI.scale(560), JBUI.scale(420))
        }
        wireKeys(panel)
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = refresh()
        })
        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, searchField.textEditor)
            .setTitle("Settings Jump")
            .setMovable(true)
            .setResizable(true)
            .setRequestFocus(true)
            .createPopup()
            .also {
                if (project != null) it.showCenteredInCurrentWindow(project) else it.showInFocusCenter()
            }
    }

    private fun refresh() {
        val state = SettingsJumpState.getInstance()
        val pages = SettingsPageIndexFacade.pagesForContext(project)
        val favoriteIds = state.favorites().map { it.configurableId }
        val results = SettingsSearchService.search(
            pages,
            searchField.text,
            favoriteIds,
            state.recent().map { it.configurableId },
        )
        val items = mutableListOf<SettingsJumpItem>()
        if (SettingsSearchService.normalize(searchField.text).isEmpty()) {
            val presentIds = pages.map { it.id }.toSet()
            state.favorites()
                .filter { it.configurableId !in presentIds }
                .forEach { items.add(SettingsJumpItem.MissingFavorite(it)) }
        }
        items.addAll(results.map { SettingsJumpItem.Page(it) })
        listModel.replaceAll(items)
        if (items.isNotEmpty()) list.selectedIndex = 0
    }

    private fun wireKeys(panel: JPanel) {
        val editor = searchField.textEditor
        bind(editor, "DOWN", "sj-down") { moveSelection(1) }
        bind(editor, "UP", "sj-up") { moveSelection(-1) }
        bind(editor, "ENTER", "sj-open") { openSelected() }
        bind(editor, "shift ENTER", "sj-favorite") { toggleFavoriteOnSelected() }
        for (digit in 0..9) {
            val slot = if (digit == 0) 10 else digit
            val stroke = if (SystemInfo.isMac) "meta $digit" else "control $digit"
            bind(panel, stroke, "sj-slot-$slot") { assignSlotToSelected(slot) }
        }
    }

    private fun bind(component: JComponent, keystroke: String, name: String, action: () -> Unit) {
        component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(keystroke), name)
        component.actionMap.put(name, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) = action()
        })
    }

    private fun moveSelection(delta: Int) {
        val size = listModel.size
        if (size == 0) return
        val next = ((list.selectedIndex + delta) % size + size) % size
        list.selectedIndex = next
        list.ensureIndexIsVisible(next)
    }

    private fun openSelected() {
        when (val item = list.selectedValue) {
            is SettingsJumpItem.Page -> {
                popup?.closeOk(null)
                SettingsNavigationService.open(project, item.page.id)
            }
            is SettingsJumpItem.MissingFavorite ->
                SettingsJumpNotifier.warn(
                    project,
                    "\"${item.entry.lastKnownDisplayName}\" is unavailable. " +
                        "Press Shift+Enter to remove it from favorites.",
                )
            null -> Unit
        }
    }

    private fun toggleFavoriteOnSelected() {
        when (val item = list.selectedValue) {
            is SettingsJumpItem.Page -> SettingsJumpState.getInstance().toggleFavorite(item.page)
            is SettingsJumpItem.MissingFavorite ->
                SettingsJumpState.getInstance().removeFavorite(item.entry.configurableId)
            null -> return
        }
        refresh()
    }

    private fun assignSlotToSelected(slot: Int) {
        val item = list.selectedValue as? SettingsJumpItem.Page ?: return
        val entry = SettingsJumpState.getInstance().assignSlot(slot, item.page)
        val message =
            if (entry != null) "Slot $slot -> ${item.page.displayName}"
            else "Slot $slot cleared"
        SettingsJumpNotifier.info(project, message)
    }

    private fun hintLabel(): JBLabel {
        val mod = if (SystemInfo.isMac) "Cmd" else "Ctrl"
        return JBLabel("Enter: open    Shift+Enter: favorite    $mod+1..0: assign slot").apply {
            foreground = UIUtil.getContextHelpForeground()
            border = JBUI.Borders.empty(4, 8)
        }
    }
}
