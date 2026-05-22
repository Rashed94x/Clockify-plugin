package no.paral.clockifyplugin.settings

import no.paral.clockifyplugin.api.ClockifyClient
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class ClockifySettingsConfigurable(private val project: Project) : Configurable {

    private val panel = ClockifySettingsPanel()

    override fun getDisplayName() = "Clockify"

    override fun createComponent(): JComponent {
        panel.onValidateToken = { onValidate() }
        panel.onRemoveToken = { onRemoveToken() }
        panel.onWorkspaceSelected = { ws ->
            val token = panel.token
            if (token != null) loadProjects(token, ws.id, preselectId = null)
        }
        return panel.createComponent().also { reset() }
    }

    // ── Token actions ─────────────────────────────────────────────────────────

    private fun onValidate() {
        val token = panel.token ?: run { panel.setTokenStatus("Enter a token first."); return }
        panel.setTokenStatus("Validating…")
        val modality = ModalityState.defaultModalityState()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val user = ClockifyClient(token).getUser()
                ApplicationManager.getApplication().invokeLater({
                    panel.setTokenStatus("Connected as ${user.name} (${user.email})")
                    panel.setProjectSectionVisible(true)
                    loadWorkspaces(token)
                }, modality)
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    panel.setTokenStatus("Error: ${e.message}")
                }, modality)
            }
        }
    }

    private fun onRemoveToken() {
        if (ClockifyCredentials.apiToken.isNullOrBlank()) {
            panel.setTokenStatus("No token to remove.")
            return
        }
        ClockifyCredentials.apiToken = null
        ClockifyProjectSettings.getInstance(project).state.apply {
            workspaceId = ""; workspaceName = ""; projectId = ""; projectName = ""
        }
        panel.setToken(null)
        panel.setTokenStatus("Token removed.")
        panel.resetCombos()
        panel.setProjectSectionVisible(false)
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private fun loadWorkspaces(token: String) {
        val savedState = ClockifyProjectSettings.getInstance(project).state
        panel.setComboStatus("Loading workspaces…")
        val modality = ModalityState.defaultModalityState()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val workspaces = ClockifyClient(token).getWorkspaces()
                ApplicationManager.getApplication().invokeLater({
                    panel.setWorkspaces(
                        workspaces.map { ClockifySettingsPanel.WorkspaceItem(it.id, it.name) },
                        preselectId = savedState.workspaceId
                    )
                    panel.setComboStatus("")
                    panel.selectedWorkspaceItem?.let { loadProjects(token, it.id, savedState.projectId) }
                }, modality)
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    panel.setComboStatus("Failed to load workspaces: ${e.message}")
                }, modality)
            }
        }
    }

    private fun loadProjects(token: String, workspaceId: String, preselectId: String?) {
        val modality = ModalityState.defaultModalityState()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val projects = ClockifyClient(token).getProjects(workspaceId)
                ApplicationManager.getApplication().invokeLater({
                    val items = listOf(ClockifySettingsPanel.ProjectItem("", "(none)")) +
                            projects.map { ClockifySettingsPanel.ProjectItem(it.id, it.name) }
                    panel.setProjects(items, preselectId)
                }, modality)
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    panel.setComboStatus("Failed to load projects: ${e.message}")
                }, modality)
            }
        }
    }

    // ── Configurable lifecycle ────────────────────────────────────────────────

    override fun isModified(): Boolean {
        val savedToken = ClockifyCredentials.apiToken ?: ""
        if (savedToken != (panel.token ?: "")) return true
        val saved = ClockifyProjectSettings.getInstance(project).state
        if (saved.workspaceId != (panel.selectedWorkspaceItem?.id ?: "")) return true
        if (saved.projectId != (panel.selectedProjectItem?.id ?: "")) return true
        return saved.logOnPush != panel.currentLogOnPush
    }

    override fun apply() {
        ClockifyCredentials.apiToken = panel.token
        val settings = ClockifyProjectSettings.getInstance(project)
        val ws = panel.selectedWorkspaceItem
        val pr = panel.selectedProjectItem
        settings.state.workspaceId = ws?.id ?: ""
        settings.state.workspaceName = ws?.name ?: ""
        settings.state.projectId = if (pr?.id.isNullOrBlank()) "" else pr.id
        settings.state.projectName = if (pr?.id.isNullOrBlank()) "" else pr.name
        settings.state.logOnPush = panel.currentLogOnPush
    }

    override fun reset() {
        panel.setToken(ClockifyCredentials.apiToken)
        panel.setTokenStatus("")
        val hasToken = !ClockifyCredentials.apiToken.isNullOrBlank()
        panel.setProjectSectionVisible(hasToken)
        val token = panel.token
        if (token != null) loadWorkspaces(token)
        else panel.setComboStatus("Enter and validate your API token first.")
        panel.currentLogOnPush = ClockifyProjectSettings.getInstance(project).state.logOnPush
        panel.resetTriggerPanel()
    }

    override fun disposeUIResources() {
        panel.dispose()
    }
}
