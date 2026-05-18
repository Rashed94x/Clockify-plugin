package no.paral.clockifyplugin.vcs

import no.paral.clockifyplugin.settings.ClockifyProjectSettings
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
                val trigger = ClockifyProjectSettings.getInstance(project).state.logTimeTrigger
                if (trigger != ClockifyProjectSettings.LogTimeTrigger.AFTER_COMMIT.name) return
                val commitMessage = panel.commitMessage
                ApplicationManager.getApplication().invokeLater {
                    LogTimeDialog(project, commitMessage).show()
                }
            }
        }
}
