# Clockify Plugin — Manual Test Plan

Run through every section before merging to `main` or publishing a new
version. Mark each item ✅ pass or ❌ fail. For failures, note the
observed behaviour in a comment.

**Prerequisites**
- A real Clockify account with at least one workspace, one project, and one task.
- A second invalid API token string (e.g. `invalidtoken123`) for negative tests.
- A Git repository open in the IDE.
- Plugin installed from the latest local build (`./gradlew buildPlugin`).

---

## 1. Settings Page

### 1.1 Token Validation

| # | Steps | Expected result |
|---|---|---|
| T-01 | Open Settings → Tools → Clockify. Click **Validate Token** without entering anything. | Status shows `"Enter a token first."` No API call is made. |
| T-02 | Enter the invalid token. Click **Validate Token**. | Status shows `"Error: Clockify API error 401: …"` within 15 s. |
| T-03 | Enter the real API token. Click **Validate Token**. | Status shows `"Connected as <your name> (<your email>)"`. Workspace dropdown populates immediately after. |
| T-04 | Disconnect from the internet. Enter the real token. Click **Validate Token**. | Status shows an error message (connection refused / timeout) within 15 s. Does not hang indefinitely. |
| T-05 | Reconnect internet. Enter the real token. Click **Validate Token** again. | Validates successfully (same as T-03). |

### 1.2 Workspace & Project Dropdowns

| # | Steps | Expected result |
|---|---|---|
| T-06 | After T-03, inspect the **Default Workspace** dropdown. | Lists all workspaces on the account. The first workspace is selected. |
| T-07 | Change the **Default Workspace** to a different entry. | **Default Project** dropdown clears and reloads with projects belonging to the newly selected workspace. |
| T-08 | Select a project in the **Default Project** dropdown. | Selection is held — no automatic reset. |
| T-09 | Select **(none)** in the **Default Project** dropdown. | Selection is held as `(none)`. |
| T-10 | Click **Cancel** without clicking Apply. Re-open Settings → Tools → Clockify. | Dropdowns show the previously saved values (or empty if nothing was saved before). |
| T-11 | Select a workspace and project. Click **Apply**. Close and re-open Settings. | The saved workspace and project are pre-selected. |

### 1.3 Settings Persistence Across Restarts

| # | Steps | Expected result |
|---|---|---|
| T-12 | Configure token + workspace + project. Click Apply. Fully restart the IDE. Re-open Settings → Tools → Clockify. | Token field is populated (masked). Workspace and project dropdowns show the saved selections after the workspaces load. |

### 1.4 Apply / Reset Behaviour

| # | Steps | Expected result |
|---|---|---|
| T-13 | Change the project dropdown. Click **Reset** (not Apply). | Dropdown reverts to the last saved value. |
| T-14 | Change the project dropdown. Click **Apply**. Change it again. Click **Reset**. | Dropdown reverts to the value saved in T-14, not the original. |
| T-15 | Make no changes in the settings panel. Check the **Apply** button state. | **Apply** is disabled (no unsaved modifications). |
| T-16 | Type a single character into the token field. | **Apply** becomes enabled. |

---

## 2. Log Time Dialog

### 2.1 Dialog Trigger

| # | Steps | Expected result |
|---|---|---|
| T-17 | Make a commit via the IDE commit dialog (`Cmd+K` / `Ctrl+K`). | Log Time dialog appears automatically after the commit completes. |
| T-18 | Make a commit from an external terminal (`git commit` in a terminal outside the IDE). | Dialog does **not** appear (plugin only hooks into IDE-initiated commits). |
| T-19 | Open the Log Time dialog (via a commit). Click **Cancel** immediately. | Dialog closes. No entry appears in Clockify. |
| T-20 | Open the Log Time dialog. Close it with the window **X** button. | Same as Cancel — no entry created. |

### 2.2 Pre-filled Fields

| # | Steps | Expected result |
|---|---|---|
| T-21 | Check out a branch named `feature/PROJ-42-add-login`. Make a commit. | Title field reads `"Feature Proj 42 Add Login"`. |
| T-22 | Check out a branch with no `/` (e.g. `main`). Make a commit. | Title field reads `"Main"` (the whole branch name, capitalised). |
| T-23 | Make a commit with the message `"fix: resolve login loop"`. Open the dialog. | Description area contains `"fix: resolve login loop"`. |
| T-24 | Make a commit with a multi-line commit message. | Full commit message appears in the description area with line breaks preserved. |
| T-25 | Open the dialog. Edit both the Title and Description fields manually. | Fields accept free text. The OK button is not disabled by editing. |

### 2.3 Duration Field

| # | Steps | Expected result |
|---|---|---|
| T-26 | Leave Duration empty. Click **Log Time**. | Error message `"Enter a valid duration …"` shown in red. Dialog stays open. |
| T-27 | Type `abc`. Click **Log Time**. | Same error as T-26. |
| T-28 | Type `0`. Click **Log Time**. | Same error as T-26 (zero is not a valid duration). |
| T-29 | Type `1h 30m`. Click **Log Time** (with valid token + workspace). | Entry created in Clockify with duration 1 h 30 m. |
| T-30 | Type `45m`. Click **Log Time**. | Entry created with duration 45 min. |
| T-31 | Type `2h`. Click **Log Time**. | Entry created with duration 2 h. |
| T-32 | Type `90` (no unit). Click **Log Time**. | Entry created with duration 90 min (plain number = minutes). |
| T-33 | Type `1.5h`. Click **Log Time**. | Entry created with duration 1 h 30 m. |

### 2.4 Project & Task Dropdowns

| # | Steps | Expected result |
|---|---|---|
| T-34 | Open dialog. Inspect the **Project** dropdown. | Pre-selected to the default project saved in settings (or `(none)` if none was saved). |
| T-35 | Change the **Project** dropdown to a different project. | **Task** dropdown clears and reloads with tasks belonging to the new project. |
| T-36 | Select **(none)** in Project. | Task dropdown resets to `(no task)`. |
| T-37 | Select a project that has no tasks. | Task dropdown shows only `(no task)`. |
| T-38 | Select a project and a specific task. Click **Log Time**. | Entry in Clockify is tagged with the selected task. |
| T-39 | Select a project but leave Task as `(no task)`. Click **Log Time**. | Entry created with the correct project and no task attached. |
| T-40 | Leave Project as `(none)` and Task as `(no task)`. Fill Duration. Click **Log Time**. | Entry created in Clockify with no project and no task. |

### 2.5 Successful Submission

| # | Steps | Expected result |
|---|---|---|
| T-41 | Submit a valid entry (duration + project). | OK button becomes disabled during the API call, then the dialog closes. |
| T-42 | After T-41, check the IDE notification area. | A green balloon notification reads `"Time logged successfully!"`. |
| T-43 | After T-41, open Clockify in the browser and inspect the time entries. | Entry exists with the correct start time, end time (= start + duration), description, project, and optional task. |
| T-44 | Verify the entry's start time relative to when you clicked **Log Time**. | `end` ≈ time of click; `start` = end − duration. Both in UTC. |

### 2.6 Error Handling in the Dialog

| # | Steps | Expected result |
|---|---|---|
| T-45 | Delete the API token via Settings → Apply. Then make a commit and fill the dialog. Click **Log Time**. | Inline error `"API token not configured — go to Settings → Tools → Clockify."` OK button re-enables. |
| T-46 | Clear the workspace in settings. Make a commit. Click **Log Time**. | Inline error `"No workspace configured — go to Settings → Tools → Clockify."` |
| T-47 | Disconnect internet. Fill the dialog. Click **Log Time**. | OK button disables, then re-enables. Inline error shows connection/timeout message. Dialog stays open. |
| T-48 | After T-47, reconnect internet and click **Log Time** again. | Entry is created successfully (same as T-41). |

---

## 3. API Integration

### 3.1 Endpoint Coverage

| # | What to verify | How |
|---|---|---|
| T-49 | `GET /user` is called during Validate Token. | Observe the status label update in the settings page (T-03). |
| T-50 | `GET /workspaces` is called after successful validation. | Workspace dropdown populates after T-03. |
| T-51 | `GET /workspaces/{id}/projects` is called when workspace is selected. | Project dropdown updates when workspace changes (T-07). |
| T-52 | `GET /workspaces/{id}/projects/{id}/tasks` is called when project is selected in the dialog. | Task dropdown updates when project changes in dialog (T-35). |
| T-53 | `POST /workspaces/{id}/time-entries` is called on Log Time. | Entry appears in Clockify (T-43). |

### 3.2 Timeout Behaviour

| # | Steps | Expected result |
|---|---|---|
| T-54 | Block outbound traffic to `api.clockify.me` via firewall/proxy. Validate token. | Error appears within **15 seconds** — not later, not indefinitely. |
| T-55 | Same firewall block. Click **Log Time** in the dialog. | Dialog shows inline error within 15 s. OK button re-enables. |

---

## 4. Multi-Project Setup

| # | Steps | Expected result |
|---|---|---|
| T-56 | Open **Project A** in the IDE. Set its default to Workspace W1 / Project P1. Apply. | Saved correctly (verify by re-opening settings). |
| T-57 | Open **Project B** in a new IDE window. Set its default to Workspace W1 / Project P2. Apply. | Saved correctly, independent of Project A's setting. |
| T-58 | Switch back to Project A's window. Open Settings → Tools → Clockify. | Shows W1 / P1 — not P2. |
| T-59 | Make a commit in Project A. Open the dialog. | Project dropdown pre-selects P1. |
| T-60 | Make a commit in Project B. Open the dialog. | Project dropdown pre-selects P2. |

---

## 5. Edge Cases

| # | Steps | Expected result |
|---|---|---|
| T-61 | Open a non-Git project (no `.git` folder). Make a commit via another VCS (e.g. SVN) or open the dialog manually. | Title field is empty. Dialog is otherwise fully functional. |
| T-62 | Open a Git project with a detached HEAD (no branch name). Make a commit. | Title field is empty. No crash or error. |
| T-63 | Set a default project in settings. That project is then archived or deleted in Clockify. Open the dialog. | Project dropdown loads; the deleted project is absent. No crash. |
| T-64 | Enter a very long commit message (500+ characters). Open the dialog. | Description area contains the full message with scroll. Dialog layout is not broken. |
| T-65 | Enter a commit message containing special characters (`<`, `>`, `"`, `&`, emoji). | Message appears verbatim in the dialog and is sent as-is to the Clockify API. |
| T-66 | Rapidly make two commits back-to-back before closing the first dialog. | Two separate dialogs appear (or the second queues behind the first). No crash. |

---

## 6. Tool Window

### 6.1 Visibility and Layout

| # | Steps | Expected result |
|---|---|---|
| T-71 | Open any project with the plugin installed. Look at the right sidebar. | A **Clockify** tab is present. Clicking it opens the tool window panel. |
| T-72 | Go to **View → Tool Windows → Clockify**. | Tool window opens if it was closed. |
| T-73 | Open the tool window with a valid token and workspace/project configured. | Panel shows a **Log Time…** button, a separator, the workspace name, and the default project name. |
| T-74 | Open the tool window with no API token configured. | Panel shows the **Log Time…** button and an inline note: *"No API token configured. Open Settings → Tools → Clockify to get started."* |
| T-75 | Configure a token but select no workspace. Open the tool window. | Panel shows the button and an inline note about no workspace being selected. |

### 6.2 Settings Refresh

| # | Steps | Expected result |
|---|---|---|
| T-76 | Open the tool window. Note the displayed project name. Change the default project in Settings → Apply. Switch away from the tool window tab and back. | Tool window now shows the updated project name without an IDE restart. |
| T-77 | Open the tool window before configuring any settings (no token). Configure token + workspace + project in Settings → Apply. Switch away from the tool window and back. | Tool window now shows the workspace and project names; the "not configured" prompt is gone. |

### 6.3 Manual Log Time Trigger

| # | Steps | Expected result |
|---|---|---|
| T-78 | Open the tool window. Click **Log Time…**. | The **Log Time to Clockify** dialog opens — the exact same dialog that appears after a commit. |
| T-79 | Open the dialog via the tool window. Check the **Title** field. | Pre-filled from the current Git branch name (same behaviour as the post-commit dialog). |
| T-80 | Open the dialog via the tool window. Check the **Description** field. | Field is **empty** (no commit message available from this trigger). |
| T-81 | Open the dialog via the tool window. Check the **Project** dropdown. | Pre-selected to the default project saved in settings (same as post-commit). |
| T-82 | Fill Duration and click **Log Time** from the tool window dialog. | Entry is created in Clockify. Success balloon notification appears. |
| T-83 | Click **Cancel** in the dialog opened from the tool window. | Dialog closes. No entry created. Tool window is still visible. |
| T-84 | Open the tool window in a non-Git project. Click **Log Time…**. | Dialog opens. Title field is empty. No crash or error. All other fields function normally. |

---

## 7. Compatibility



| # | Steps | Expected result |
|---|---|---|
| T-67 | Install the plugin in **PyCharm** (non-IDEA IDE). Open Settings → Tools → Clockify. | Settings page loads and functions identically to IntelliJ IDEA. |
| T-68 | Install the plugin in **WebStorm**. Make a commit. | Log Time dialog appears correctly. |
| T-69 | Install the plugin in an IDE with Git support **disabled** (remove Git4Idea plugin if possible). Make a commit. | Plugin still loads. Dialog opens. Title is empty. No crash or error log related to missing Git4Idea. |
| T-70 | Install the plugin in an IDE running on **JVM 17** (IntelliJ 2024.1). | Plugin loads and all features work (verifies JVM 17 bytecode target is correct). |

---

## Pass Criteria

All items in sections 1–6 must pass before a release. Section 7 should pass
on at least two different JetBrains IDEs.

Any ❌ failure must be logged as a GitHub issue with:
- Test case number
- IDE name and version
- Observed behaviour
- Steps to reproduce
