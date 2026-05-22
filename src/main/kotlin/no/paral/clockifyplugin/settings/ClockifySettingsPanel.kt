package no.paral.clockifyplugin.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ClockifySettingsPanel {

    // Callbacks wired by ClockifySettingsConfigurable
    var onValidateToken: (() -> Unit)? = null
    var onRemoveToken: (() -> Unit)? = null
    var onWorkspaceSelected: ((WorkspaceItem) -> Unit)? = null

    private var tokenField: JBPasswordField? = null
    private var tokenStatus: JLabel? = null
    private var workspaceCombo: ComboBox<WorkspaceItem>? = null
    private var projectCombo: ComboBox<ProjectItem>? = null
    private var comboStatus: JLabel? = null
    private var projectSettingsWrapper: JPanel? = null
    private var outerPanel: JComponent? = null
    private var projectDialogPanel: DialogPanel? = null
    private var logOnPushCheckBox: JBCheckBox? = null

    var currentLogOnPush: Boolean
        get() = logOnPushCheckBox?.isSelected ?: true
        set(value) { logOnPushCheckBox?.isSelected = value }
    var isUpdatingCombos = false

    // ── Read accessors ────────────────────────────────────────────────────────

    val token: String?
        get() = tokenField?.password?.let { String(it) }?.trim()?.ifBlank { null }

    val selectedWorkspaceId: String?
        get() = (workspaceCombo?.selectedItem as? WorkspaceItem)?.id

    val selectedWorkspaceItem: WorkspaceItem?
        get() = workspaceCombo?.selectedItem as? WorkspaceItem

    val selectedProjectItem: ProjectItem?
        get() = projectCombo?.selectedItem as? ProjectItem

    // ── Write methods ─────────────────────────────────────────────────────────

    fun setToken(value: String?) {
        tokenField?.text = value ?: ""
    }

    fun setTokenStatus(text: String) {
        tokenStatus?.text = text
    }

    fun setComboStatus(text: String) {
        comboStatus?.text = text
    }

    fun setProjectSectionVisible(visible: Boolean) {
        projectSettingsWrapper?.isVisible = visible
        outerPanel?.revalidate()
        outerPanel?.repaint()
    }

    fun setWorkspaces(items: List<WorkspaceItem>, preselectId: String?) {
        isUpdatingCombos = true
        try {
            workspaceCombo?.model = DefaultComboBoxModel(items.toTypedArray())
            val idx = items.indexOfFirst { it.id == preselectId }
            workspaceCombo?.selectedIndex = if (idx >= 0) idx else if (items.isNotEmpty()) 0 else -1
        } finally {
            isUpdatingCombos = false
        }
    }

    fun setProjects(items: List<ProjectItem>, preselectId: String?) {
        val idx = if (preselectId != null) items.drop(1).indexOfFirst { it.id == preselectId } else -1
        projectCombo?.model = DefaultComboBoxModel(items.toTypedArray())
        projectCombo?.selectedIndex = if (idx >= 0) idx + 1 else 0
    }

    fun resetCombos() {
        workspaceCombo?.model = DefaultComboBoxModel()
        projectCombo?.model = DefaultComboBoxModel()
        comboStatus?.text = ""
    }

    fun resetTriggerPanel() {
        projectDialogPanel?.reset()
    }

    // ── Component builder ─────────────────────────────────────────────────────

    fun createComponent(): JComponent {
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
            onWorkspaceSelected?.invoke(selected)
        }

        val projectPanel = panel {
            group("Project Settings") {
                row("Default Workspace:") { cell(wCombo).resizableColumn() }
                row("Default Project:") { cell(pCombo).resizableColumn() }
                row { cell(cStatus) }
            }
            group("Automatic Trigger") {
                row {
                    checkBox("Trigger log time popup after push").also { cb ->
                        logOnPushCheckBox = cb.component
                    }
                }
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
                    button("Validate Token") { onValidateToken?.invoke() }
                    button("Remove Token") { onRemoveToken?.invoke() }
                }
                row { cell(tStatus) }
            }
            row { cell(wrapper).resizableColumn().align(Align.FILL) }
        }
        outerPanel = root
        return root
    }

    fun dispose() {
        tokenField = null
        tokenStatus = null
        workspaceCombo = null
        projectCombo = null
        comboStatus = null
        projectSettingsWrapper = null
        outerPanel = null
        projectDialogPanel = null
        logOnPushCheckBox = null
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    data class WorkspaceItem(val id: String, val name: String) {
        override fun toString() = name
    }

    data class ProjectItem(val id: String, val name: String) {
        override fun toString() = name
    }
}
