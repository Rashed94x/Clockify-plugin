package no.paral.clockifyplugin.settings

import no.paral.clockifyplugin.api.ClockifyClient
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.MutableProperty
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import com.intellij.openapi.ui.ComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ClockifySettingsConfigurable(private val project: Project) : Configurable {

    private var tokenField: JBPasswordField? = null
    private var tokenStatus: JLabel? = null
    private var workspaceCombo: ComboBox<WorkspaceItem>? = null
    private var projectCombo: ComboBox<ProjectItem>? = null
    private var comboStatus: JLabel? = null
    private var projectSettingsWrapper: JPanel? = null
    private var outerPanel: JComponent? = null
    private var hasValidToken = false
    private var currentTrigger = ClockifyProjectSettings.LogTimeTrigger.AFTER_PUSH
    private var projectDialogPanel: DialogPanel? = null

    // Prevents the workspace action listener from firing loadProjects during programmatic model updates
    private var isUpdatingCombos = false

    override fun getDisplayName() = "Clockify"

    override fun createComponent(): JComponent {
        val field = JBPasswordField()
        val tStatus = JLabel("")
        val wCombo = ComboBox<WorkspaceItem>()
        val pCombo = ComboBox<ProjectItem>()
        val cStatus = JLabel("")

        tokenField = field
        tokenStatus = tStatus
        workspaceCombo = wCombo
        projectCombo = pCombo
        comboStatus = cStatus

        wCombo.addActionListener {
            if (isUpdatingCombos) return@addActionListener
            val selected = wCombo.selectedItem as? WorkspaceItem ?: return@addActionListener
            loadProjects(currentToken() ?: return@addActionListener, selected.id, preselectId = null)
        }

        val projectPanel = panel {
            group("Project Settings") {
                row("Default Workspace:") { cell(wCombo).resizableColumn() }
                row("Default Project:") { cell(pCombo).resizableColumn() }
                row { cell(cStatus) }
            }
            group("Automatic Trigger") {
                buttonsGroup("Show log time dialog:") {
                    row {
                        radioButton("After push", ClockifyProjectSettings.LogTimeTrigger.AFTER_PUSH)
                            .comment("Recommended — triggers once per push")
                    }
                    row {
                        radioButton("After commit", ClockifyProjectSettings.LogTimeTrigger.AFTER_COMMIT)
                            .comment("Triggers after every commit")
                    }
                }.bind(
                    object : MutableProperty<ClockifyProjectSettings.LogTimeTrigger> {
                        override fun get() = currentTrigger
                        override fun set(value: ClockifyProjectSettings.LogTimeTrigger) { currentTrigger = value }
                    },
                    ClockifyProjectSettings.LogTimeTrigger::class.java
                )
            }

        }.also { projectDialogPanel = it }
        val wrapper = JPanel(BorderLayout()).apply { add(projectPanel, BorderLayout.CENTER) }
        projectSettingsWrapper = wrapper

        val root = panel {
            group("API Token") {
                row("API Token:") {
                    cell(field)
                        .columns(COLUMNS_LARGE)
                        .comment("Get your token from clockify.me → Profile Settings → API")
                }
                row {
                    button("Validate Token") { onValidate() }
                    button("Remove Token") { onRemoveToken() }
                }
                row { cell(tStatus) }
            }
            row { cell(wrapper).resizableColumn() }
        }
        outerPanel = root
        return root.also { reset() }
    }

    private fun currentToken(): String? =
        tokenField?.password?.let { String(it) }?.trim()?.ifBlank { null }

    private fun onRemoveToken() {
        if (ClockifyCredentials.apiToken.isNullOrBlank()) {
            tokenStatus?.text = "No token to remove."
            return
        }
        ClockifyCredentials.apiToken = null
        ClockifyProjectSettings.getInstance(project).state.apply {
            workspaceId = ""; workspaceName = ""; projectId = ""; projectName = ""
        }
        tokenField?.text = ""
        tokenStatus?.text = "Token removed."
        workspaceCombo?.model = DefaultComboBoxModel()
        projectCombo?.model = DefaultComboBoxModel()
        comboStatus?.text = ""
        hasValidToken = false
        updateProjectSectionVisibility()
    }

    private fun updateProjectSectionVisibility() {
        projectSettingsWrapper?.isVisible = hasValidToken
        outerPanel?.revalidate()
        outerPanel?.repaint()
    }

    private fun onValidate() {
        val token = currentToken() ?: run {
            tokenStatus?.text = "Enter a token first."
            return
        }
        tokenStatus?.text = "Validating…"
        val modality = ModalityState.defaultModalityState()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val user = ClockifyClient(token).getUser()
                ApplicationManager.getApplication().invokeLater({
                    tokenStatus?.text = "Connected as ${user.name} (${user.email})"
                    hasValidToken = true
                    updateProjectSectionVisibility()
                    loadWorkspaces(token)
                }, modality)
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    tokenStatus?.text = "Error: ${e.message}"
                }, modality)
            }
        }
    }

    private fun loadWorkspaces(token: String) {
        val savedState = ClockifyProjectSettings.getInstance(project).state
        val savedWorkspaceId = savedState.workspaceId
        val savedProjectId = savedState.projectId
        comboStatus?.text = "Loading workspaces…"
        val modality = ModalityState.defaultModalityState()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val workspaces = ClockifyClient(token).getWorkspaces()
                ApplicationManager.getApplication().invokeLater({
                    isUpdatingCombos = true
                    try {
                        val items = workspaces.map { WorkspaceItem(it.id, it.name) }.toTypedArray()
                        workspaceCombo?.model = DefaultComboBoxModel(items)
                        val idx = workspaces.indexOfFirst { it.id == savedWorkspaceId }
                        workspaceCombo?.selectedIndex = if (idx >= 0) idx else if (workspaces.isNotEmpty()) 0 else -1
                    } finally {
                        isUpdatingCombos = false
                    }
                    comboStatus?.text = ""
                    (workspaceCombo?.selectedItem as? WorkspaceItem)?.let {
                        loadProjects(token, it.id, preselectId = savedProjectId)
                    }
                }, modality)
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    comboStatus?.text = "Failed to load workspaces: ${e.message}"
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
                    val items = (listOf(ProjectItem("", "(none)")) +
                            projects.map { ProjectItem(it.id, it.name) }).toTypedArray()
                    projectCombo?.model = DefaultComboBoxModel(items)
                    val idx = if (preselectId != null) projects.indexOfFirst { it.id == preselectId } else -1
                    projectCombo?.selectedIndex = if (idx >= 0) idx + 1 else 0
                }, modality)
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    comboStatus?.text = "Failed to load projects: ${e.message}"
                }, modality)
            }
        }
    }

    override fun isModified(): Boolean {
        val savedToken = ClockifyCredentials.apiToken ?: ""
        if (savedToken != (currentToken() ?: "")) return true
        val saved = ClockifyProjectSettings.getInstance(project).state
        val wId = (workspaceCombo?.selectedItem as? WorkspaceItem)?.id ?: ""
        val pId = (projectCombo?.selectedItem as? ProjectItem)?.id ?: ""
        if (saved.workspaceId != wId || saved.projectId != pId) return true
        return saved.logTimeTrigger != currentTrigger.name
    }

    override fun apply() {
        ClockifyCredentials.apiToken = currentToken()
        val settings = ClockifyProjectSettings.getInstance(project)
        val wItem = workspaceCombo?.selectedItem as? WorkspaceItem
        val pItem = projectCombo?.selectedItem as? ProjectItem
        settings.state.workspaceId = wItem?.id ?: ""
        settings.state.workspaceName = wItem?.name ?: ""
        settings.state.projectId = if (pItem?.id.isNullOrBlank()) "" else pItem!!.id
        settings.state.projectName = if (pItem?.id.isNullOrBlank()) "" else pItem!!.name
        settings.state.logTimeTrigger = currentTrigger.name
    }

    override fun reset() {
        tokenField?.text = ClockifyCredentials.apiToken ?: ""
        tokenStatus?.text = ""
        hasValidToken = !ClockifyCredentials.apiToken.isNullOrBlank()
        val token = currentToken()
        if (token != null) loadWorkspaces(token)
        else comboStatus?.text = "Enter and validate your API token first."
        currentTrigger = try {
            ClockifyProjectSettings.LogTimeTrigger.valueOf(
                ClockifyProjectSettings.getInstance(project).state.logTimeTrigger
            )
        } catch (_: IllegalArgumentException) {
            ClockifyProjectSettings.LogTimeTrigger.AFTER_PUSH
        }
        projectDialogPanel?.reset()  // syncs radio button UI to currentTrigger
        updateProjectSectionVisibility()
    }

    override fun disposeUIResources() {
        tokenField = null
        tokenStatus = null
        workspaceCombo = null
        projectCombo = null
        comboStatus = null
        projectSettingsWrapper = null
        outerPanel = null
        projectDialogPanel = null
    }

    private data class WorkspaceItem(val id: String, val name: String) {
        override fun toString() = name
    }

    private data class ProjectItem(val id: String, val name: String) {
        override fun toString() = name
    }
}
