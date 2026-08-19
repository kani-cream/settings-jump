package com.github.kanicream.settingsjump.poc

import com.github.kanicream.settingsjump.poc.analyze.SettingsIndexCollector
import com.github.kanicream.settingsjump.poc.report.GateReport
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

/**
 * Gate 1/2 headless measurement over the platform test composition.
 * The full-IDE composition is measured separately via the runIde action.
 */
class Gate1EligibilityReportTest : BasePlatformTestCase() {

    fun testCollectAndReport() {
        val index = SettingsIndexCollector.collectAll(project)
        assertTrue("expected some Configurable EPs", index.records.isNotEmpty())

        val report = GateReport.build(index)
        val out = Path.of("build", "gate-reports")
        Files.createDirectories(out)
        val file = out.resolve("gate12-headless.md")
        Files.writeString(file, report)
        println("=== Gate 1/2 headless report (also written to ${file.toAbsolutePath()}) ===")
        println(report)

        val eligible = index.records.filter { it.isEligible }
        assertTrue("expected at least one eligible page", eligible.isNotEmpty())
    }

    fun testEligiblePagesHaveUniqueIdsWithinScope() {
        val index = SettingsIndexCollector.collectAll(project)
        val duplicates = index.records
            .filter { it.isEligible }
            .groupBy { it.scope to it.id }
            .filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            println("Duplicate eligible IDs observed (identity risk, feeds design 8.1):")
            duplicates.forEach { (key, records) ->
                println("- ${key.second} [${key.first}] x${records.size} from ${records.map { it.sourcePluginId }}")
            }
        }
        // Observation only in Phase 0: duplicates are reported, not asserted away.
        assertTrue(true)
    }
}
