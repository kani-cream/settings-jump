package com.github.kanicream.settingsjump.poc

import com.github.kanicream.settingsjump.poc.analyze.SettingsIndexCollector
import com.github.kanicream.settingsjump.poc.model.AvailabilityKind
import com.github.kanicream.settingsjump.poc.navigation.PocSettingsNavigator
import com.github.kanicream.settingsjump.poc.navigation.PocSettingsNavigator.PreflightResult
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Gate 3 headless checks: preflight fail-closed behavior and EP lookup cost.
 * Actually opening the Settings dialog is validated manually via runIde
 * (headless test environments cannot show the dialog).
 */
class Gate3PreflightTest : BasePlatformTestCase() {

    fun testMissingIdFailsClosed() {
        val result = PocSettingsNavigator.preflight(project, "settings.jump.poc.nonexistent.page")
        assertTrue("missing ID must be Unavailable", result is PreflightResult.Unavailable)
    }

    fun testStaticEligiblePagesPassPreflight() {
        val index = SettingsIndexCollector.collectAll(project)
        val candidates = index.records
            .filter { it.isEligible && it.availability == AvailabilityKind.STATIC && it.nestedDepth == 0 }
            .take(20)
        assertTrue("expected static eligible candidates", candidates.isNotEmpty())

        val failures = candidates.mapNotNull { record ->
            val result = PocSettingsNavigator.preflight(project, record.id!!)
            if (result is PreflightResult.Unavailable) record.id to result.reason else null
        }
        if (failures.isNotEmpty()) {
            println("STATIC eligible pages failing preflight (investigate):")
            failures.forEach { (id, reason) -> println("- $id: $reason") }
        }
        assertTrue(
            "most static eligible pages should pass preflight, failed: ${failures.size}/${candidates.size}",
            failures.size <= candidates.size / 2,
        )
    }

    fun testEpLookupIsFast() {
        val index = SettingsIndexCollector.collectAll(project)
        val someId = index.records.first { it.isEligible }.id!!
        val start = System.nanoTime()
        repeat(50) {
            PocSettingsNavigator.findEp(project, someId)
        }
        val avgMillis = (System.nanoTime() - start) / 1_000_000.0 / 50
        println("findEp average: %.2f ms over ${index.records.size} pages".format(avgMillis))
        assertTrue("metadata lookup should be fast, was $avgMillis ms", avgMillis < 50)
    }
}
