package com.github.rashed94x.clockifyplugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "ClockifyProjectSettings", storages = [Storage("clockify.xml")])
class ClockifyProjectSettings : PersistentStateComponent<ClockifyProjectSettings.State> {

    class State {
        var workspaceId: String = ""
        var workspaceName: String = ""
        var projectId: String = ""
        var projectName: String = ""
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
