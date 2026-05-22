package no.paral.clockifyplugin.startup

import no.paral.clockifyplugin.vcs.ClockifyPushListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import git4idea.push.GitPushListener

class ClockifyStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.messageBus
            .connect(project)
            .subscribe(GitPushListener.TOPIC, ClockifyPushListener(project))
    }
}
