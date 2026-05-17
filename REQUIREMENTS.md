# Clockify Plugin — Requirements

## Overview
A JetBrains IDE plugin that integrates with the Clockify time-tracking API.
It supports all IntelliJ-platform IDEs (IntelliJ IDEA, PyCharm, WebStorm,
GoLand, Rider, CLion, etc.) with no IDE-specific code.

---

## 1. Settings Page ✅ Implemented

### 1.1 Global Settings
- A dedicated settings page under **Settings → Tools → Clockify**.
- **API Token** field (password-masked input) where the user enters their
  Clockify personal API token.
- A **Validate Token** button that calls the Clockify API and shows a
  success/error message inline.
- On success, the detected workspace(s) are fetched and made available
  for project-level settings.

### 1.2 Per-Project Settings
- A settings page under **Settings → Tools → Clockify → Project**.
- **Default Workspace** dropdown — populated after a valid API token is saved.
- **Default Project** dropdown — populated based on the selected workspace,
  lists all Clockify projects the user is a member of.
- Settings are stored per IDE project (using `PropertiesComponent` or
  project-level storage), so different IDE projects can point to different
  Clockify projects.

### 1.3 Known Issues ✅ Fixed
- **Validate Token hangs** ✅ — replaced `java.net.http.HttpClient` with `HttpURLConnection` (with explicit connect/read timeouts) in `ClockifyClient.kt`. The Java 11 HTTP client's internal thread pool conflicted with IntelliJ's class loader in the plugin sandbox.
- **Workspace change does not refresh projects** ✅ — added `isUpdatingCombos` guard flag in `ClockifySettingsConfigurable`. Programmatic model/index updates during async load now skip the action listener; only user-initiated combo changes trigger `loadProjects`.
- **Two separate settings pages** ✅ — merged `ClockifyGlobalConfigurable` and `ClockifyProjectConfigurable` into a single `ClockifySettingsConfigurable` (project-level). The page shows an "API Token" section at the top and a "Project Settings" section below, registered as one entry under Settings → Tools → Clockify.

---

## 2. Commit-Time Log Dialog ✅ Implemented

### 2.1 Trigger
- The dialog appears automatically **after** the user successfully makes a
  commit (post-commit VCS listener).
- The dialog is **optional**: the user can dismiss or cancel it without any
  time entry being created.

### 2.2 Auto-filled Fields
The dialog pre-populates the following from VCS context:
- **Title** — derived from the current Git branch name
  (e.g., `feature/PROJ-42-add-login` → `PROJ-42 Add Login`).
- **Description** — the last commit message.

### 2.3 Editable Fields
| Field | Type | Notes |
|---|---|---|
| Title | Text input | Pre-filled from branch name; user can edit |
| Description | Text area | Pre-filled from commit message; user can edit |
| Duration | Text input | Format: `1h 30m` or `90m`; required to submit |
| Clockify Project | Dropdown | Defaults to the project-level default; user can override |
| Clockify Task | Dropdown | Lists tasks within the selected Clockify project; optional |

### 2.4 Actions
- **Log Time** button — validates fields and calls Clockify API to create a
  time entry; shows a notification toast on success or error.
- **Cancel / Close** button — dismisses the dialog; no API call is made.

---

## 3. API Integration ✅ Implemented

- All Clockify API calls use the token stored in global settings.
- Endpoints used:
  - `GET /user` — validate token and fetch user info.
  - `GET /workspaces` — list workspaces.
  - `GET /workspaces/{workspaceId}/projects` — list projects.
  - `GET /workspaces/{workspaceId}/projects/{projectId}/tasks` — list tasks.
  - `POST /workspaces/{workspaceId}/time-entries` — create a time entry.
- API calls run on a background thread (coroutine / `ApplicationManager.getApplication().executeOnPooledThread`); UI is never blocked.
- API base URL: `https://api.clockify.me/api/v1`

---

## 4. Compatibility & Platform ✅ Implemented

- Built on the **IntelliJ Platform SDK** — no IDE-specific APIs.
- Minimum supported IntelliJ Platform version: **2024.1** (to cover all
  actively supported JetBrains IDEs).
- Language: **Kotlin**.
- Distributed as a single plugin JAR compatible with all JetBrains IDEs
  that run on the IntelliJ Platform.

---

## 5. Tool Window ✅ Implemented

- A **Clockify tool window** is available in the IDE sidebar (right side by default).
- Contains a **Log Time…** button that opens the same dialog used after a commit
  (`LogTimeDialog`), with an empty description field (no commit message context).
- Displays the currently configured **workspace name** and **default project name**
  beneath the button so the user can see their active context at a glance.
- If no API token is configured, shows an inline prompt directing the user to
  Settings → Tools → Clockify.
- If a token exists but no workspace is selected, shows a similar prompt.
- Panel content **refreshes automatically** each time the tool window is made
  visible, so it always reflects the latest saved settings without an IDE restart.

---

## 6. Non-Goals (out of scope for v1)
- Automatic timer start/stop (time is logged manually via the commit dialog only).
- Tracking idle time or active coding time.
- Multi-workspace time entries in a single submission.
- OAuth / browser-based authentication (API token only).
