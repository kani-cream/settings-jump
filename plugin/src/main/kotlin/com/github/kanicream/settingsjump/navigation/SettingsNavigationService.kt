package com.github.kanicream.settingsjump.navigation

import com.github.kanicream.settingsjump.index.SettingsPageIndexFacade
import com.github.kanicream.settingsjump.model.SettingsPage
import com.github.kanicream.settingsjump.model.SettingsScope
import com.github.kanicream.settingsjump.state.SettingsJumpState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import java.util.function.Predicate

/**
 * Preflight + navigation (design.md 10.2). Preflight runs for every page.
 * The ShowSettingsUtil call boundary converts expected navigation failures into
 * a notification; platform cancellations are rethrown, nothing leaks to the IDE.
 */
object SettingsNavigationService {

    private val log = logger<SettingsNavigationService>()

    sealed interface Outcome {
        data object Opened : Outcome
        data class NotOpened(val userMessage: String) : Outcome
    }

    /** Opens the page identified by [configurableId]; fail closed on any doubt. */
    fun open(project: Project?, configurableId: String): Outcome {
        val outcome = preflightAndOpen(project, configurableId)
        if (outcome is Outcome.NotOpened) {
            SettingsJumpNotifier.warn(project, outcome.userMessage)
        }
        return outcome
    }

    private fun preflightAndOpen(project: Project?, configurableId: String): Outcome {
        val page = SettingsPageIndexFacade.findById(project, configurableId)
            ?: return Outcome.NotOpened(
                "Settings page \"$configurableId\" is not available in the current context.",
            )
        if (page.scope == SettingsScope.PROJECT && project == null) {
            return Outcome.NotOpened("\"${page.displayName}\" requires an open project.")
        }
        val ep = findEp(project, configurableId)
            ?: return Outcome.NotOpened(
                "Settings page \"${page.displayName}\" is not available in the current context.",
            )
        when (val preflight = preflight(ep)) {
            is Preflight.Unavailable ->
                return Outcome.NotOpened(
                    "\"${page.displayName}\" is not available here (${preflight.reason}).",
                )
            Preflight.Ok -> Unit
        }
        return try {
            ShowSettingsUtil.getInstance().showSettingsDialog(
                project,
                Predicate<Configurable> { (it as? ConfigurableWithId)?.id == configurableId },
                null,
            )
            SettingsJumpState.getInstance().recordRecent(page)
            Outcome.Opened
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.warn("navigation failed for $configurableId", e)
            Outcome.NotOpened("Could not open \"${page.displayName}\".")
        }
    }

    private sealed interface Preflight {
        data object Ok : Preflight
        data class Unavailable(val reason: String) : Preflight
    }

    private fun preflight(ep: ConfigurableEP<*>): Preflight {
        if (!ep.isAvailable) return Preflight.Unavailable("not available in this project")
        return try {
            if (ep.canCreateConfigurable()) Preflight.Ok
            else Preflight.Unavailable("not provided in this context")
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.warn("preflight failed", e)
            Preflight.Unavailable("preflight failed")
        }
    }

    private fun findEp(project: Project?, targetId: String): ConfigurableEP<*>? {
        val roots: List<ConfigurableEP<*>> =
            Configurable.APPLICATION_CONFIGURABLE.extensionList +
                (project?.let { Configurable.PROJECT_CONFIGURABLE.getExtensions(it) } ?: emptyList())
        return roots.firstNotNullOfOrNull { findInTree(it, targetId) }
    }

    private fun findInTree(ep: ConfigurableEP<*>, targetId: String): ConfigurableEP<*>? {
        if (ep.id == targetId) return ep
        return ep.children.orEmpty().firstNotNullOfOrNull { findInTree(it, targetId) }
    }
}
