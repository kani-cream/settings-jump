package com.github.kanicream.settingsjump.poc.report

import com.github.kanicream.settingsjump.poc.model.CollectedIndex
import com.github.kanicream.settingsjump.poc.model.IneligibleReason
import com.github.kanicream.settingsjump.poc.model.PageRecord

/** Formats Gate 1 / Gate 2 measurements as a markdown report. */
object GateReport {

    private val DESIGN_TARGET_KEYWORDS = listOf(
        "Keymap", "Appearance", "Proxy", "Code Style", "Code Completion",
        "Plugins", "Git", "Gradle", "Editor",
    )

    fun build(index: CollectedIndex): String {
        val records = index.records
        val eligible = records.filter { it.isEligible }
        val reasonCounts = IneligibleReason.entries.associateWith { reason ->
            records.count { reason in it.ineligibleReasons() }
        }
        val knownIds = records.mapNotNull { it.id }.toSet()
        val knownGroupIds = records.mapNotNull { it.groupId }.toSet()
        val unresolvedParents = records
            .filter { it.nestedDepth == 0 && it.parentId != null }
            .filter { it.parentId !in knownIds }
            .groupBy { it.parentId!! }

        return buildString {
            appendLine("# Settings Jump PoC — Gate 1 / Gate 2 Report")
            appendLine()
            appendLine("## Totals")
            appendLine()
            appendLine("| Metric | Value |")
            appendLine("|---|---|")
            appendLine("| Total pages observed | ${records.size} |")
            appendLine("| APPLICATION scope | ${records.count { it.scope.name == "APPLICATION" }} |")
            appendLine("| PROJECT scope | ${records.count { it.scope.name == "PROJECT" }} |")
            appendLine("| Nested (declared as XML children) | ${records.count { it.nestedDepth > 0 }} |")
            appendLine("| **Eligible (strict, page-level)** | **${eligible.size} (${percent(eligible.size, records.size)})** |")
            appendLine("| Collection time | ${index.collectionMillis} ms |")
            appendLine()
            appendLine("## Gate 1 — Ineligibility reasons (a page may have several)")
            appendLine()
            appendLine("| Reason | Count |")
            appendLine("|---|---|")
            reasonCounts.forEach { (reason, count) -> appendLine("| $reason | $count |") }
            appendLine()
            val onlyChildFlags = records.count {
                val r = it.ineligibleReasons()
                r.isNotEmpty() && r.all { reason ->
                    reason == IneligibleReason.DYNAMIC_CHILDREN || reason == IneligibleReason.CHILDREN_EP_NAME
                }
            }
            appendLine("Pages ineligible ONLY due to dynamic/childrenEPName flags: $onlyChildFlags")
            appendLine()
            appendLine("## Gate 1 — Display name sources")
            appendLine()
            appendLine("| Source | Count |")
            appendLine("|---|---|")
            records.groupingBy { it.displayNameSource }.eachCount()
                .forEach { (source, count) -> appendLine("| $source | $count |") }
            appendLine()
            appendLine("## Gate 2 — Context and child-supply observations")
            appendLine()
            appendLine("| Observation | Count |")
            appendLine("|---|---|")
            appendLine("| CONTEXTUAL (provider or nonDefaultProject) | ${records.count { it.declaresProviderOrNonDefault }} |")
            appendLine("| dynamic=true | ${records.count { it.dynamic }} |")
            appendLine("| childrenEPName declared | ${records.count { it.childrenEpName != null }} |")
            appendLine()
            appendLine("### childrenEPName probe (are referenced EPs enumerable from metadata alone?)")
            appendLine()
            index.childrenEpRecords.forEach { (epName, children) ->
                val eligibleChildren = children.count { it.isEligible }
                appendLine("- `$epName`: ${children.size} page(s), $eligibleChildren would be eligible")
                children.take(15).forEach { appendLine("    - ${describe(it)}") }
            }
            index.childrenEpProbeErrors.forEach { (epName, error) ->
                appendLine("- `$epName`: ENUMERATION FAILED — $error")
            }
            appendLine()
            appendLine("## Parent resolution (top-level pages whose parentId is not another page id)")
            appendLine()
            appendLine("These usually reference group ids. Observed group ids: ${knownGroupIds.sorted()}")
            appendLine()
            unresolvedParents.entries.sortedByDescending { it.value.size }.take(30).forEach { (parent, pages) ->
                appendLine("- `$parent` <- ${pages.size} page(s)")
            }
            appendLine()
            appendLine("## Design target pages (design.md Gate 1 examples)")
            appendLine()
            DESIGN_TARGET_KEYWORDS.forEach { keyword ->
                val hits = records.filter { matchesKeyword(it, keyword) }
                appendLine("### $keyword — ${hits.size} hit(s)")
                hits.take(8).forEach { appendLine("- ${describe(it)}") }
                appendLine()
            }
            appendLine("## Non-eligible pages (up to 60)")
            appendLine()
            records.filter { !it.isEligible }.take(60).forEach {
                appendLine("- ${describe(it)} reasons=${it.ineligibleReasons()}")
            }
        }
    }

    private fun matchesKeyword(record: PageRecord, keyword: String): Boolean {
        val needle = keyword.lowercase()
        return record.displayName?.lowercase()?.contains(needle) == true ||
            record.id?.lowercase()?.contains(needle.replace(" ", "")) == true ||
            record.key?.lowercase()?.contains(needle.replace(" ", ".")) == true
    }

    private fun describe(record: PageRecord): String =
        "`${record.id ?: "(no id)"}` name=${record.displayName ?: record.key ?: "(none)"} " +
            "scope=${record.scope} avail=${record.availability} plugin=${record.sourcePluginId} " +
            (if (record.nestedDepth > 0) "nested " else "") +
            (if (record.dynamic) "dynamic " else "") +
            (record.childrenEpName?.let { "childrenEPName=$it " } ?: "")

    private fun percent(part: Int, total: Int): String =
        if (total == 0) "n/a" else "%.1f%%".format(part * 100.0 / total)
}
