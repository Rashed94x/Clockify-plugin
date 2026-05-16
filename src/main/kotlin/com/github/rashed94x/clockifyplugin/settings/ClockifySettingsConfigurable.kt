package com.github.rashed94x.clockifyplugin.settings

import com.github.rashed94x.clockifyplugin.api.ClockifyClient
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel

class ClockifySettingsConfigurable(private val project: Project) : Configurable {

    private var tokenField: JBPasswordField? = null
    private var tokenStatus: JLabel? = null
    private var workspaceCombo: JComboBox<WorkspaceItem>? = null
    private var projectCombo: JComboBox<ProjectItem>? = null
    private var comboStatus: JLabel? = null

    // Prevents the workspace action listener from firing loadProjects during programmatic model updates
    private var isUpdatingCombos = false

    override fun getDisplayName() = "Clockify"

    override fun createComponent(): JComponent {
        val field = JBPasswordField()
        val tStatus = JLabel("")
        val wCombo = JComboBox<WorkspaceItem>()
        val pCombo = JComboBox<ProjectItem>()
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

        return panel {
            group("API Token") {
                row("API Token:") {
                    cell(field)
                        .columns(COLUMNS_LARGE)
                        .comment("Get your token from clockify.me → Profile Settings → API")
                }
                row {
                    button("Validate Token") { onValidate() }
                    cell(tStatus)
                }
            }
            group("Project Settings") {
                row("Default Workspace:") { cell(wCombo).resizableColumn() }
                row("Default Project:") { cell(pCombo).resizableColumn() }
                row { cell(cStatus) }
            }
        }.also { reset() }
    }

    private fun currentToken(): String? =
        tokenField?.password?.let { String(it) }?.trim()?.ifBlank { null }

    private fun onValidate() {
        val token = currentToken() ?: run {
            tokenStatus?.text = "Enter a token first."
            return
        }
        tokenStatus?.text = "Validating…"
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val user = ClockifyClient(token).getUser()
                ApplicationManager.getApplication().invokeLater {
                    tokenStatus?.text = "Connected as ${user.name} (${user.email})"
                    loadWorkspaces(token)
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    tokenStatus?.text = "Error: ${e.message}"
                }
            }
        }
    }

    private fun loadWorkspaces(token: String) {
        val savedState = ClockifyProjectSettings.getInstance(project).state
        val savedWorkspaceId = savedState.workspaceId
        val savedProjectId = savedState.projectId
        comboStatus?.text = "Loading workspaces…"
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val workspaces = ClockifyClient(token).getWorkspaces()
                ApplicationManager.getApplication().invokeLater {
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
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    comboStatus?.text = "Failed to load workspaces: ${e.message}"
                }
            }
        }
    }

    private fun loadProjects(token: String, workspaceId: String, preselectId: String?) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val projects = ClockifyClient(token).getProjects(workspaceId)
                ApplicationManager.getApplication().invokeLater {
                    val items = (listOf(ProjectItem("", "(none)")) +
                            projects.map { ProjectItem(it.id, it.name) }).toTypedArray()
                    projectCombo?.model = DefaultComboBoxModel(items)
                    val idx = if (preselectId != null) projects.indexOfFirst { it.id == preselectId } else -1
                    projectCombo?.selectedIndex = if (idx >= 0) idx + 1 else 0
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    comboStatus?.text = "Failed to load projects: ${e.message}"
                }
            }
        }
    }

    override fun isModified(): Boolean {
        val savedToken = ClockifyCredentials.apiToken ?: ""
        if (savedToken != (currentToken() ?: "")) return true
        val saved = ClockifyProjectSettings.getInstance(project).state
        val wId = (workspaceCombo?.selectedItem as? WorkspaceItem)?.id ?: ""
        val pId = (projectCombo?.selectedItem as? ProjectItem)?.id ?: ""
        return saved.workspaceId != wId || saved.projectId != pId
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
    }

    override fun reset() {
        tokenField?.text = ClockifyCredentials.apiToken ?: ""
        tokenStatus?.text = ""
        val token = currentToken()
        if (token != null) loadWorkspaces(token)
        else comboStatus?.text = "Enter and validate your API token first."
    }

    override fun disposeUIResources() {
        tokenField = null
        tokenStatus = null
        workspaceCombo = null
        projectCombo = null
        comboStatus = null
    }

    private data class WorkspaceItem(val id: String, val name: String) {
        override fun toString() = name
    }

    private data class ProjectItem(val id: String, val name: String) {
        override fun toString() = name
    }
}
