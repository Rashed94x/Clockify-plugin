# Clockify Plugin — Developer Documentation

## Table of Contents
1. [Project Overview](#project-overview)
2. [Repository Structure](#repository-structure)
3. [Architecture](#architecture)
4. [Package Reference](#package-reference)
   - [api](#api-package)
   - [settings](#settings-package)
   - [vcs](#vcs-package)
   - [toolwindow](#toolwindow-package)
5. [Key Data Flows](#key-data-flows)
6. [Plugin Registration (plugin.xml)](#plugin-registration)
7. [Build Configuration](#build-configuration)
8. [Adding New Features](#adding-new-features)

---

## Project Overview

A JetBrains IntelliJ Platform plugin that integrates with the [Clockify](https://clockify.me) time-tracking API. It works in all IntelliJ-based IDEs (IntelliJ IDEA, PyCharm, WebStorm, GoLand, CLion, etc.) without IDE-specific code.

**Language:** Kotlin  
**Min platform version:** IntelliJ Platform 2024.1 (build `241`)  
**JVM target:** 17  
**Plugin ID:** `com.github.rashed94x.clockifyplugin`

---

## Repository Structure

```
Clockify-plugin/
├── build.gradle.kts                  # Gradle build — dependencies, JVM target, patchPluginXml
├── settings.gradle.kts               # Plugin management, repository config
├── gradle.properties                 # Group, version, Gradle cache flags
├── REQUIREMENTS.md                   # Product requirements with implementation status
├── DEVELOPER.md                      # This file
├── USER_GUIDE.md                     # End-user documentation
├── MANUAL_TESTS.md                   # Manual test plan
└── src/
    └── main/
        ├── kotlin/com/github/rashed94x/clockifyplugin/
        │   ├── api/
        │   │   ├── ClockifyClient.kt          # HTTP client — all API calls
        │   │   └── ClockifyModels.kt          # Serializable data classes
        │   ├── settings/
        │   │   ├── ClockifyCredentials.kt         # Secure token storage (PasswordSafe)
        │   │   ├── ClockifyProjectSettings.kt     # Per-project state (workspace + project IDs)
        │   │   └── ClockifySettingsConfigurable.kt # Settings UI (Settings → Tools → Clockify)
        │   ├── toolwindow/
        │   │   └── ClockifyToolWindowFactory.kt   # IDE sidebar tool window
        │   └── vcs/
        │       ├── ClockifyCheckinHandlerFactory.kt # Post-commit VCS hook
        │       └── LogTimeDialog.kt                 # Log Time dialog (shared by VCS hook + tool window)
        └── resources/
            └── META-INF/plugin.xml        # Extension point registrations
```

---

## Architecture

The plugin is split into four independent layers that communicate only downward:

```
┌──────────────────────────────────────────────────────────────┐
│  toolwindow  (sidebar UI)                                    │
│  ClockifyToolWindowFactory  ──────────────────┐              │
└───────────────────────────────────────────────┤              │
                                                ▼              │
┌──────────────────────────────────────────────────────────────┐
│  vcs  (commit hook UI)                                       │
│  ClockifyCheckinHandlerFactory  →  LogTimeDialog  ◄──────────┘
└───────────────────┬──────────────────────────────────────────┘
                    │ reads
┌───────────────────▼──────────────────────────────────────────┐
│  settings  (state)                                           │
│  ClockifyCredentials   ClockifyProjectSettings               │
│  ClockifySettingsConfigurable                                │
└───────────────────┬──────────────────────────────────────────┘
                    │ uses
┌───────────────────▼──────────────────────────────────────────┐
│  api  (network)                                              │
│  ClockifyClient          ClockifyModels                      │
└──────────────────────────────────────────────────────────────┘
```

Both the tool window and the VCS hook share the same `LogTimeDialog`. The only
difference is the commit message passed in: the VCS hook passes the real commit
message; the tool window passes `""`.

**Threading rule:** every `ClockifyClient` call is made inside
`ApplicationManager.getApplication().executeOnPooledThread { }`. Results are
handed back to the UI thread via `invokeLater { }`. The EDT is never blocked.

---

## Package Reference

### `api` package

#### `ClockifyClient.kt`
**Path:** `src/main/kotlin/.../api/ClockifyClient.kt`

The sole HTTP client for the Clockify REST API. Instantiated with an API token and stateless — create a new instance per call site.

```
ClockifyClient(apiToken: String)
```

| Method | Endpoint | Returns |
|---|---|---|
| `getUser()` | `GET /user` | `ClockifyUser` |
| `getWorkspaces()` | `GET /workspaces` | `List<ClockifyWorkspace>` |
| `getProjects(workspaceId)` | `GET /workspaces/{id}/projects` | `List<ClockifyProject>` |
| `getTasks(workspaceId, projectId)` | `GET /workspaces/{id}/projects/{id}/tasks` | `List<ClockifyTask>` |
| `createTimeEntry(workspaceId, request)` | `POST /workspaces/{id}/time-entries` | `Unit` |

**Transport:** `java.net.HttpURLConnection` with 15 s connect/read timeouts.  
**Serialization:** `kotlinx.serialization` (`Json { ignoreUnknownKeys = true; explicitNulls = false }`).  
**Errors:** throws `ClockifyApiException(statusCode, message)` for any non-2xx response.

> **Why `HttpURLConnection` instead of `java.net.http.HttpClient`?**  
> The Java 11 HTTP client creates its own thread pool that conflicts with IntelliJ's class loader in the plugin sandbox, causing requests to hang indefinitely.

---

#### `ClockifyModels.kt`
**Path:** `src/main/kotlin/.../api/ClockifyModels.kt`

All `@Serializable` data classes used for API responses and requests.

| Class | Direction | Fields |
|---|---|---|
| `ClockifyUser` | response | `id`, `name`, `email` |
| `ClockifyWorkspace` | response | `id`, `name` |
| `ClockifyProject` | response | `id`, `name`, `archived` |
| `ClockifyTask` | response | `id`, `name` |
| `CreateTimeEntryRequest` | request | `start`, `end`, `description`, `projectId?`, `taskId?` |

`start` and `end` are ISO 8601 UTC strings (`yyyy-MM-dd'T'HH:mm:ss'Z'`). They are computed in `LogTimeDialog.doOKAction()` from `Instant.now()` minus the parsed duration.

---

### `settings` package

#### `ClockifyCredentials.kt`
**Path:** `src/main/kotlin/.../settings/ClockifyCredentials.kt`

Singleton (`object`) that wraps IntelliJ's `PasswordSafe` for secure API token storage. The token is stored in the OS keychain / IDE credential store — never in plain text on disk.

```kotlin
ClockifyCredentials.apiToken          // read
ClockifyCredentials.apiToken = value  // write (null clears the entry)
```

Internally uses `CredentialAttributes(generateServiceName("ClockifyPlugin", "apiToken"))` as the keychain key.

---

#### `ClockifyProjectSettings.kt`
**Path:** `src/main/kotlin/.../settings/ClockifyProjectSettings.kt`

Project-level service that persists the selected workspace and project per IDE project. Stored in `.idea/clockify.xml` via IntelliJ's XML state serializer.

```kotlin
ClockifyProjectSettings.getInstance(project).state  // returns State
```

`State` fields:

| Field | Description |
|---|---|
| `workspaceId` | Clockify workspace UUID |
| `workspaceName` | Display name (used in tool window) |
| `projectId` | Clockify project UUID |
| `projectName` | Display name (used in tool window) |

Registered as a `projectService` in `plugin.xml` — one instance per open IDE project, automatically loaded and saved by the platform.

---

#### `ClockifySettingsConfigurable.kt`
**Path:** `src/main/kotlin/.../settings/ClockifySettingsConfigurable.kt`

Implements `Configurable` and renders the **Settings → Tools → Clockify** page. Takes `Project` as a constructor argument (injected by the platform).

**UI layout:**
```
┌─ API Token ──────────────────────────────────────┐
│  API Token: [••••••••••••••••••••]               │
│  [Validate Token]  <status label>                │
└──────────────────────────────────────────────────┘
┌─ Project Settings ───────────────────────────────┐
│  Default Workspace: [combo ▼]                    │
│  Default Project:   [combo ▼]                    │
│  <combo status label>                            │
└──────────────────────────────────────────────────┘
```

**Key methods:**

| Method | Behaviour |
|---|---|
| `createComponent()` | Builds the panel; calls `reset()` to populate fields |
| `onValidate()` | Calls `getUser()` on a pooled thread; on success calls `loadWorkspaces()` |
| `loadWorkspaces(token)` | Fetches workspaces on pooled thread; updates workspace combo on EDT |
| `loadProjects(token, workspaceId, preselectId)` | Fetches projects; updates project combo on EDT |
| `apply()` | Saves token to `ClockifyCredentials`; saves workspace/project to `ClockifyProjectSettings` |
| `reset()` | Reloads token from `ClockifyCredentials`; triggers `loadWorkspaces()` if token exists |

**Combo refresh guard:** `isUpdatingCombos: Boolean` is set to `true` while the workspace combo model is being replaced programmatically. The workspace `ActionListener` returns early when this flag is set, preventing an infinite cascade (model change → listener → API call → model change → …). Only user-initiated selection changes trigger `loadProjects`.

---

### `toolwindow` package

#### `ClockifyToolWindowFactory.kt`
**Path:** `src/main/kotlin/.../toolwindow/ClockifyToolWindowFactory.kt`

Implements `ToolWindowFactory` and provides the **Clockify** panel in the IDE sidebar. Registered with `anchor="right"` in `plugin.xml`, so it docks to the right side by default.

**UI layout:**
```
┌─ Clockify ───────────────────────────────────────┐
│  [  Log Time…  ]                                 │
│  ────────────────                                │
│  Workspace                                       │
│  My Workspace                                    │
│  Default Project                                 │
│  My App                                          │
└──────────────────────────────────────────────────┘
```

When no token is configured:
```
┌─ Clockify ───────────────────────────────────────┐
│  [  Log Time…  ]                                 │
│  ────────────────                                │
│  No API token configured.                        │
│  Open Settings → Tools → Clockify to get started.│
└──────────────────────────────────────────────────┘
```

**Key design decisions:**

- **Shared dialog:** clicking "Log Time…" calls `LogTimeDialog(project, "").show()`. The empty string means the description field starts blank instead of pre-filled from a commit message. Everything else (branch name, project/task dropdowns, duration) works identically.
- **Content refresh:** `createToolWindowContent` is called once by the platform. To keep workspace/project labels current after the user changes settings, the inner panel is wrapped in a container that rebuilds via `HierarchyListener` every time the tool window is shown (i.e. whenever the user clicks the Clockify tab).

---

### `vcs` package

#### `ClockifyCheckinHandlerFactory.kt`
**Path:** `src/main/kotlin/.../vcs/ClockifyCheckinHandlerFactory.kt`

Extends `CheckinHandlerFactory` — the IntelliJ Platform extension point that hooks into the VCS commit workflow for all VCS systems (Git, SVN, Mercurial, etc.).

On every successful commit, `checkinSuccessful()` fires on the EDT. It reads the commit message from `CheckinProjectPanel.commitMessage` and schedules `LogTimeDialog` via `invokeLater` so the dialog opens after the commit workflow fully completes.

---

#### `LogTimeDialog.kt`
**Path:** `src/main/kotlin/.../vcs/LogTimeDialog.kt`

Extends `DialogWrapper` (IntelliJ's standard modal dialog base class). Used by both the VCS commit hook and the tool window button. Dismissible without side effects.

**Constructor:** `LogTimeDialog(project: Project, commitMessage: String)`  
Pass `""` as `commitMessage` when opening from the tool window.

**UI layout:**
```
┌─ Log Time to Clockify ──────────────────────────────┐
│  Title:       [Feature Proj 42 Add Login          ] │
│  Description: [last commit message / empty        ] │
│               [                                   ] │
│  Duration:    [         ]  e.g. 1h 30m · 45m · 2h  │
│  Project:     [My Project              ▼]           │
│  Task:        [(no task)               ▼]           │
│  <error label in red>                               │
├─────────────────────────────────────────────────────┤
│                        [Cancel]  [Log Time]         │
└─────────────────────────────────────────────────────┘
```

**Initialisation sequence (`init` block):**
1. Set title and OK button label.
2. Attach `ActionListener` to `projectCombo` (with `isUpdatingCombos` guard).
3. Call `init()` — triggers `createCenterPanel()` and wires up dialog buttons.
4. Call `loadProjects()` — async, pre-selects the saved default project.

**Key private methods:**

| Method | Description |
|---|---|
| `loadProjects()` | Reads saved `workspaceId` from `ClockifyProjectSettings`; fetches project list; pre-selects saved `projectId` |
| `loadTasks(token, workspaceId, projectId)` | Fetches tasks for the selected project; populates task combo |
| `doOKAction()` | Validates duration, reads selections, builds `CreateTimeEntryRequest`, POSTs on pooled thread; disables OK button during flight; shows balloon notification on success or inline error on failure |

**Companion object helpers:**

| Function | Description |
|---|---|
| `getBranchName(project)` | Uses `git4idea.repo.GitRepositoryManager` (optional dependency); returns `""` on any error |
| `formatBranchName(branch)` | Strips prefix up to last `/`, replaces `-`/`_` with spaces, title-cases each word |
| `parseDuration(input)` | Parses `"1h 30m"`, `"45m"`, `"2h"`, `"90"` (plain number = minutes) → total seconds; returns `null` for invalid input |

**Duration formats accepted:**

| Input | Seconds |
|---|---|
| `1h 30m` | 5400 |
| `1h` | 3600 |
| `45m` | 2700 |
| `1.5h` | 5400 |
| `90` | 5400 (plain number = minutes) |

---

## Key Data Flows

### 1. First-time token setup
```
User opens Settings → Tools → Clockify
  → ClockifySettingsConfigurable.createComponent()
  → reset() → no token found → shows "Enter and validate your API token first."

User types token → clicks "Validate Token"
  → onValidate()
  → [pooled thread] ClockifyClient.getUser()
  → [EDT] show "Connected as …" + loadWorkspaces(token)
  → [pooled thread] ClockifyClient.getWorkspaces()
  → [EDT] populate workspace combo, select saved or first
  → loadProjects(token, workspaceId, savedProjectId)
  → [pooled thread] ClockifyClient.getProjects(workspaceId)
  → [EDT] populate project combo, pre-select saved project

User clicks Apply
  → apply()
  → ClockifyCredentials.apiToken = token        (PasswordSafe)
  → ClockifyProjectSettings.state.workspaceId = …  (.idea/clockify.xml)
```

### 2. Logging time after a commit
```
User makes a Git commit in the IDE
  → ClockifyCheckinHandlerFactory.checkinSuccessful()
  → invokeLater → LogTimeDialog(project, commitMessage).show()

Dialog opens
  → getBranchName() → GitRepositoryManager → currentBranchName
  → formatBranchName() → title field pre-filled
  → commitMessage → description area pre-filled
  → loadProjects() → [pooled thread] getProjects() → project combo populated
  → (on project select) loadTasks() → [pooled thread] getTasks() → task combo populated

User fills duration, optionally changes project/task, clicks "Log Time"
  → doOKAction() → parseDuration() → durationSeconds
  → Instant.now() → start = now − durationSeconds, end = now
  → [pooled thread] ClockifyClient.createTimeEntry(workspaceId, request)
  → [EDT] super.doOKAction() + balloon "Time logged successfully!"
```

### 3. Logging time manually from the tool window
```
User clicks the Clockify tab in the IDE sidebar
  → ClockifyToolWindowFactory.buildContainer() refreshes via HierarchyListener
  → buildPanel() reads ClockifyProjectSettings + ClockifyCredentials
  → Shows workspace/project context labels (or a setup prompt if not configured)

User clicks "Log Time…"
  → LogTimeDialog(project, "").show()
  → Same flow as #2 above, except description area starts empty
```

---

## Plugin Registration

`src/main/resources/META-INF/plugin.xml`

| Extension | Class | Purpose |
|---|---|---|
| `projectConfigurable` | `ClockifySettingsConfigurable` | Settings page under Tools → Clockify |
| `projectService` | `ClockifyProjectSettings` | Per-project workspace/project persistence |
| `checkinHandlerFactory` | `ClockifyCheckinHandlerFactory` | Post-commit dialog trigger |
| `notificationGroup` id=`"Clockify"` | — | Balloon notification for successful time log |
| `toolWindow` id=`"Clockify"` | `ClockifyToolWindowFactory` | Sidebar panel with manual Log Time button |

**Dependencies:**
- `com.intellij.modules.platform` — required; makes the plugin load in all IntelliJ-based IDEs.
- `Git4Idea` — optional (`<depends optional="true">`); only used for branch name detection in `LogTimeDialog`. The plugin loads and works fully without it — the Title field just starts empty.

---

## Build Configuration

`build.gradle.kts`

| Setting | Value | Reason |
|---|---|---|
| `intellijIdea("2025.2.6.2")` | IDE to build/test against | Latest stable at time of development |
| `bundledPlugin("Git4Idea")` | Compile-time Git4Idea access | Needed to call `GitRepositoryManager` without reflection |
| `jvmToolchain(17)` | JVM 17 bytecode target | IntelliJ Platform 2024.1 runs on JVM 17 |
| `sinceBuild = "241"` | Min compatible version = 2024.1 | Declared in patched plugin.xml |
| `untilBuild = ""` | No upper bound | Plugin stays installable on all future IDE versions |
| `kotlinx-serialization-json:1.7.3` | JSON parsing | Replaces reflection-based Gson; compile-time safe |

---

## Adding New Features

### Add a new Clockify API call
1. Add a `@Serializable` data class to `ClockifyModels.kt` if a new response shape is needed.
2. Add a method to `ClockifyClient` using the existing `get()` or `post()` private helpers.
3. Call the method from a pooled thread at the call site.

### Add a field to the log-time dialog
1. Declare a new Swing component as an instance variable in `LogTimeDialog`.
2. Add it to the `panel {}` block inside `createCenterPanel()`.
3. Read its value in `doOKAction()` and include it in `CreateTimeEntryRequest` (or a future extended request).
4. The change automatically applies to both the VCS-triggered dialog and the tool window button since they share the same class.

### Persist a new per-project setting
1. Add a `var` field to `ClockifyProjectSettings.State`.
2. Read/write it via `ClockifyProjectSettings.getInstance(project).state`.
3. The platform automatically serializes the new field to `.idea/clockify.xml`.

### Add content to the tool window panel
Edit `ClockifyToolWindowFactory.buildPanel()`. The panel is rebuilt on every visibility change, so reads of settings state are always fresh — no manual refresh logic needed.

### Add a new global (IDE-wide) setting
Create a new `@Service(Service.Level.APP)` + `PersistentStateComponent` class, register it as an `applicationService` in `plugin.xml`, and add corresponding UI to `ClockifySettingsConfigurable`.
