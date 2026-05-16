package com.github.rashed94x.clockifyplugin.vcs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory

class ClockifyCheckinHandlerFactory : CheckinHandlerFactory() {

    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
        object : CheckinHandler() {
            override fun checkinSuccessful() {
                val project = panel.project
                val commitMessage = panel.commitMessage
                ApplicationManager.getApplication().invokeLater {
                    LogTimeDialog(project, commitMessage).show()
                }
            }
        }
}
