# Clockify Plugin — User Guide

Log time to Clockify without leaving your IDE. A dialog lets you record how
long you worked, which project it belongs to, and what you were doing. It
opens automatically after every commit, and is also available on demand from
the Clockify tool window in the sidebar.

---

## Table of Contents
1. [Requirements](#requirements)
2. [Installation](#installation)
3. [Getting your Clockify API token](#getting-your-clockify-api-token)
4. [Configuring the plugin](#configuring-the-plugin)
   - [Step 1 — Enter and validate your token](#step-1--enter-and-validate-your-token)
   - [Step 2 — Set a default project](#step-2--set-a-default-project)
5. [Logging time after a commit](#logging-time-after-a-commit)
   - [Duration format](#duration-format)
6. [Logging time manually from the tool window](#logging-time-manually-from-the-tool-window)
7. [Working across multiple projects](#working-across-multiple-projects)
8. [Troubleshooting](#troubleshooting)

---

## Requirements

- Any JetBrains IDE (IntelliJ IDEA, PyCharm, WebStorm, GoLand, CLion, Rider, …) version **2024.1 or later**
- A [Clockify](https://clockify.me) account (free tier is enough)
- An internet connection when validating the token or logging time

---

## Installation

> Until the plugin is published to the JetBrains Marketplace, install it
> from a local build.

**Build the plugin JAR:**
```
./gradlew buildPlugin
```
The ZIP is produced at `build/distributions/Clockify-plugin-<version>.zip`.

**Install in the IDE:**
1. Open **Settings** (`Cmd+,` / `Ctrl+Alt+S`).
2. Go to **Plugins**.
3. Click the **⚙ gear icon** → **Install Plugin from Disk…**
4. Select the ZIP file from `build/distributions/`.
5. Restart the IDE when prompted.

---

## Getting your Clockify API token

1. Log in to [clockify.me](https://clockify.me).
2. Click your avatar (top-right) → **Profile Settings**.
3. Scroll to the **API** section at the bottom of the page.
4. Click **Generate** if no token exists, then copy the token string.

Keep this token private — it gives full access to your Clockify account.

---

## Configuring the plugin

Open **Settings** (`Cmd+,` / `Ctrl+Alt+S`) → **Tools** → **Clockify**.

### Step 1 — Enter and validate your token

1. Paste your API token into the **API Token** field.
2. Click **Validate Token**.
3. Wait a moment — the field next to the button will show either:
   - **"Connected as Your Name (your@email.com)"** — token is valid, workspaces loaded.
   - **"Error: …"** — the token is wrong or there is no internet connection.

Once connected the **Default Workspace** dropdown fills automatically.

### Step 2 — Set a default project

1. Choose your **Default Workspace** from the first dropdown.  
   Changing the workspace immediately refreshes the project list.
2. Choose your **Default Project** from the second dropdown.  
   Select **(none)** if you want to pick a project each time in the dialog.
3. Click **Apply** or **OK**.

The default project is saved per IDE project, so you can have different
defaults for each repository you open.

---

## Logging time after a commit

After every successful commit a **Log Time to Clockify** dialog appears
automatically.

```
┌─ Log Time to Clockify ─────────────────────────────┐
│                                                     │
│  Title:       Feature Proj 42 Add Login             │
│  Description: fix: resolve login redirect loop      │
│                                                     │
│  Duration:    1h 30m                                │
│                                                     │
│  Project:     My App  ▼                             │
│  Task:        (no task)  ▼                          │
│                                                     │
│                          [Cancel]  [Log Time]       │
└─────────────────────────────────────────────────────┘
```

| Field | Pre-filled from | Editable |
|---|---|---|
| **Title** | Current Git branch name | Yes |
| **Description** | Last commit message | Yes |
| **Duration** | *(empty — you must type it)* | Yes |
| **Project** | Your saved default project | Yes |
| **Task** | *(none)* | Yes |

**To log time:** fill in the Duration, confirm or edit the other fields,
then click **Log Time**. A green notification appears at the bottom of the
IDE on success.

**To skip:** click **Cancel** or close the dialog. Nothing is sent to Clockify.

### Duration format

Type your duration in the **Duration** field using any of these formats:

| You type | Logged |
|---|---|
| `1h 30m` | 1 hour 30 minutes |
| `1h` | 1 hour |
| `45m` | 45 minutes |
| `1.5h` | 1 hour 30 minutes |
| `90` | 90 minutes (plain number = minutes) |

Duration is **required** — the **Log Time** button will not submit without it.

---

## Logging time manually from the tool window

You don't have to wait for a commit to log time. The **Clockify tool window**
lets you open the same dialog at any moment.

**Opening the tool window:**
- Click **Clockify** in the right sidebar of the IDE.
- If it is not visible, go to **View → Tool Windows → Clockify**.

**What you'll see:**

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

The workspace and default project shown are the ones saved in your settings
for the current repository. They refresh automatically each time you open the
tool window, so they always reflect your latest configuration.

**Clicking Log Time…** opens the exact same dialog as the post-commit trigger.
The only difference is that the **Description** field starts empty instead of
being pre-filled from a commit message — you can type whatever you like.
The **Title** field is still pre-filled from your current Git branch name.

If you haven't configured a token or workspace yet, the tool window shows a
prompt directing you to **Settings → Tools → Clockify**.

---

## Working across multiple projects

The **Default Workspace** and **Default Project** are saved separately for
each IDE project you open. To configure a different default for a different
repository:

1. Open that repository in the IDE.
2. Go to **Settings → Tools → Clockify**.
3. Select the workspace and project for that repository.
4. Click **Apply**.

The API token is shared across all projects — you only enter it once.

---

## Troubleshooting

**"Validating…" never resolves**  
Check your internet connection. If you are behind a corporate proxy, make
sure the IDE's proxy settings (Settings → Appearance & Behavior → System
Settings → HTTP Proxy) are configured correctly.

**Workspace or project dropdown is empty**  
Click **Validate Token** again. The dropdowns only populate after a
successful validation. If your token was changed on the Clockify website,
paste the new one and validate again.

**The log-time dialog does not appear after a commit**  
The plugin listens for commits made through the IDE's built-in VCS tools
(the Commit window, `Ctrl+K` / `Cmd+K`). Commits made in an external
terminal are not detected.

**"API token not configured" error in the dialog**  
Go to Settings → Tools → Clockify, enter your token, click Validate, and
press Apply before committing again.

**"No workspace configured" error in the dialog**  
Go to Settings → Tools → Clockify and select a Default Workspace, then
click Apply.

**Title field is empty (no branch name)**  
The branch name is read from Git. If the project is not a Git repository,
or Git support is not enabled in the IDE, the Title field starts empty —
just type a title manually.

**Time entry appears in Clockify with the wrong time zone**  
All times are submitted in UTC and Clockify converts them to your account's
time zone automatically. Check your time zone setting in Clockify's Profile
Settings if entries appear at unexpected times.
