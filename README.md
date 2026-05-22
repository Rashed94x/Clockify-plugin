# Clockify Integration — JetBrains IDE Plugin

![Build](https://github.com/Rashed94x/Clockify-plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

> **Unofficial plugin** — not affiliated with, endorsed by, or created by [Clockify](https://clockify.me) / CAKE.com.

Log time to [Clockify](https://clockify.me) directly from your JetBrains IDE. A dialog appears after each Git push so you can record your time without leaving the IDE.

---

## Features

- **Auto-prompt after push** — triggered automatically on every successful Git push
- **Clockify tool window** — log time manually at any point from the sidebar
- **Pre-filled from Git** — description defaults to your latest commit message
- **Workspace & project picker** — live dropdowns backed by the Clockify API
- **Secure token storage** — API token stored in the IDE credential safe, never in plain text

---

## Installation

**Via JetBrains Marketplace (recommended):**

`Settings / Preferences` → `Plugins` → `Marketplace` → search **"Clockify Integration"** → `Install`

**Manually:**

1. Download the latest `.zip` from [Releases](https://github.com/Rashed94x/Clockify-plugin/releases/latest)
2. `Settings / Preferences` → `Plugins` → `⚙` → `Install Plugin from Disk…`

---

## Setup

1. Open **Settings → Tools → Clockify Integration**
2. Paste your Clockify API token — find it at *clockify.me → Profile → API*
3. Click **Validate** to confirm the token and load your workspaces
4. Select your default workspace and project
5. Push a commit — the log-time dialog will appear automatically

---

## Building from source

```bash
./gradlew buildPlugin          # builds the distributable .zip
./gradlew runIde               # runs the plugin in a sandboxed IDE instance
./gradlew runPluginVerifier    # checks binary compatibility
```

Requires JDK 17+.

---

Plugin based on the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).