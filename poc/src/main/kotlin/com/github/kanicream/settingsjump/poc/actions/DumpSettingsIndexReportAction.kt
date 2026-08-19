package com.github.kanicream.settingsjump.poc.actions

import com.github.kanicream.settingsjump.poc.analyze.SettingsIndexCollector
import com.github.kanicream.settingsjump.poc.report.GateReport
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files
import java.nio.file.Path

/** Gate 1/2: dumps the metadata-only index report for the running IDE composition. */
class DumpSettingsIndexReportAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val index = SettingsIndexCollector.collectAll(project)
        val report = GateReport.build(index)
        val file: Path = Files.createTempFile("settings-jump-poc-gate12-", ".md")
        Files.writeString(file, report)
        PocNotifications.info(project, "Gate 1/2 report written: $file")
        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
        if (project != null && vFile != null) {
            OpenFileDescriptor(project, vFile).navigate(true)
        }
    }
}
