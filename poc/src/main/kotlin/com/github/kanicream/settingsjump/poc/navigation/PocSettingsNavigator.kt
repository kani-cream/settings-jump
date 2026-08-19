package com.github.kanicream.settingsjump.poc.navigation

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import java.util.function.Predicate

/**
 * Gate 3 validation target: preflight + navigation (design.md 10.2).
 * Preflight runs for every page regardless of AvailabilityKind.
 * The ShowSettingsUtil call boundary never leaks exceptions to the IDE,
 * but platform cancellations are rethrown.
 */
object PocSettingsNavigator {

    private val log = logger<PocSettingsNavigator>()

    sealed interface PreflightResult {
        data object Available : PreflightResult
        data class Unavailable(val reason: String) : PreflightResult
    }

    sealed interface NavigationResult {
        data class Opened(val preflightMillis: Long, val openMillis: Long) : NavigationResult
        data class Rejected(val reason: String) : NavigationResult
        data class Failed(val reason: String) : NavigationResult
    }

    fun preflight(project: Project?, targetId: String): PreflightResult {
        val ep = findEp(project, targetId)
            ?: return PreflightResult.Unavailable("no EP with id=$targetId in current context")
        if (!ep.isAvailable) {
            return PreflightResult.Unavailable("ep.isAvailable() == false (nonDefaultProject etc.)")
        }
        return try {
            if (ep.canCreateConfigurable()) PreflightResult.Available
            else PreflightResult.Unavailable("ep.canCreateConfigurable() == false")
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.warn("preflight failed for $targetId", e)
            PreflightResult.Unavailable("preflight threw ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun open(project: Project?, targetId: String): NavigationResult {
        val preflightStart = System.nanoTime()
        val preflight = preflight(project, targetId)
        val preflightMillis = (System.nanoTime() - preflightStart) / 1_000_000
        if (preflight is PreflightResult.Unavailable) {
            return NavigationResult.Rejected(preflight.reason)
        }
        val openStart = System.nanoTime()
        return try {
            ShowSettingsUtil.getInstance().showSettingsDialog(
                project,
                Predicate<Configurable> { (it as? ConfigurableWithId)?.id == targetId },
                null,
            )
            NavigationResult.Opened(preflightMillis, (System.nanoTime() - openStart) / 1_000_000)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.warn("navigation failed for $targetId", e)
            NavigationResult.Failed("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Metadata-only lookup over application and (if present) project EPs, including nested children. */
    fun findEp(project: Project?, targetId: String): ConfigurableEP<*>? {
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
