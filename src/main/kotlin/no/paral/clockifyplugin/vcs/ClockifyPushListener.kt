package no.paral.clockifyplugin.vcs

import no.paral.clockifyplugin.settings.ClockifyProjectSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.push.GitPushListener
import git4idea.push.GitPushRepoResult
import git4idea.repo.GitRepository

class ClockifyPushListener(private val project: Project) : GitPushListener {
    override fun onCompleted(repository: GitRepository, pushResult: GitPushRepoResult) {
        if (pushResult.type != GitPushRepoResult.Type.SUCCESS) return
        val trigger = ClockifyProjectSettings.getInstance(project).state.logTimeTrigger
        if (trigger != ClockifyProjectSettings.LogTimeTrigger.AFTER_PUSH.name) return
        val commitMessage = latestCommitMessage(repository)
        ApplicationManager.getApplication().invokeLater {
            LogTimeDialog(project, commitMessage).show()
        }
    }

    private fun latestCommitMessage(repository: GitRepository): String =
        try {
            val handler = GitLineHandler(project, repository.root, GitCommand.LOG).apply {
                addParameters("-1", "--pretty=format:%B")
            }
            Git.getInstance().runCommand(handler).getOutput().joinToString("\n").trim()
        } catch (_: Throwable) { "" }
}
