package no.paral.clockifyplugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "ClockifyProjectSettings", storages = [Storage("clockify.xml")])
class ClockifyProjectSettings : PersistentStateComponent<ClockifyProjectSettings.State> {

    enum class LogTimeTrigger { AFTER_PUSH, AFTER_COMMIT }

    class State {
        var workspaceId: String = ""
        var workspaceName: String = ""
        var projectId: String = ""
        var projectName: String = ""
        var logTimeTrigger: String = LogTimeTrigger.AFTER_PUSH.name
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): ClockifyProjectSettings =
            project.getService(ClockifyProjectSettings::class.java)
    }
}
