# 🤖 AGENTS.md — Developer & AI Agent Guide

## 📌 Project Overview
* **Repository:** `Xed-Editor`
* **Package:** `com.xed.editor`
* **Platform:** Android (Compose Multiplatform / Modern Android Stack)
* **Core Architecture:**
  * **Editor Engine:** Sora Editor integration (`soraX`) with custom syntax highlighting, TextMate grammar, LSP client, snippets, and EditorConfig support.
  * **File System Layer:** Pluggable `FileObject` abstraction (`FileWrapper`, `UriWrapper`, `NetWrapper`, `ZipFileObject`, `DroidspacesFileObject`).
  * **Container Support:** Droidspaces Linux container management and file browsing (`DroidspacesShell`, `DroidspacesManager`, `DroidspacesFileObject`).
  * **Integrated Terminal:** Droidspaces root container and Android root shell sessions with Termux view & emulator engine.

---

## 🏃 Current Sprint & Active State

### ✅ What We Did (Completed)
1. **Container File Write & Save Pipeline Fix (`DroidspacesShell.kt` & `DroidspacesFileObject.kt`):**
   * Fixed critical stream redirection bug in `getOutputStream()` where `su -c "... run cat > $path"` was intercepted by the Android host shell, attempting to write to read-only host rootfs (`/`) instead of inside the container.
   * Redirected writes through container shell execution: `run sh -c 'cat $op "$1"' _ "$path"`.
   * Wrapped container streams in `ProcessOutputStream` and `ProcessInputStream` with 32KB buffered I/O, ensuring stdin EOF flushing, process exit code validation, and explicit `IOException` throwing on write failures.
   * Added `chmod` and `chown` helper functions in `DroidspacesShell` and `DroidspacesFileObject`.
   * Updated `DroidspacesFileObject.writeText()` to strictly validate write results and propagate exceptions.

2. **Save Error Handling & Validation (`EditorTab.kt`):**
   * Enforced validation of `file.writeText(content, charset)` boolean return value in `EditorTab.write()`.
   * Prevented silent false-positive saves: on write failure, an `IOException` is thrown to trigger the error dialog and retain the dirty indicator.

3. **Real-Time Edit Revert & Dirty State Tracking (`CodeEditorState.kt` & `EditorTab.kt`):**
   * Added `savedContent` baseline tracking in `CodeEditorState`.
   * Implemented zero-allocation `checkDirty()` algorithm that compares character length and content on every text change.
   * If a user edits a file and then undos (via back button, Ctrl+Z, or manual editing) back to the saved state, `isDirty` is automatically restored to `false` and the modified marker is removed.
   * Pruned unnecessary auto-save debounces when reverting to clean state.

4. **Performance & Loading Optimizations:**
   * **Parallel EditorConfig Loading:** Made `.editorconfig` resolution run concurrently in the background in `EditorTab.init`, eliminating sequential blocking during initial content stream reading and rendering.
   * **Non-Blocking Process Draining:** Updated `DroidspacesShell.runCommand()` to asynchronously drain `stdout` and `stderr` using `CompletableFuture`, preventing pipe-buffer deadlocks on large directory/stat listings.
   * **Terminal UI Smoothening:** Removed redundant `setTextSize()` and palette reset invocations during keyboard (IME) animations in `TerminalScreen.kt`, eliminating frame drops.
   * **Subpixel Font Rendering:** Enabled `mTextPaint.setSubpixelText(true)` in `TerminalRenderer.java` for crisper, smoother terminal text.
   * **Tab Pager Viewport Optimization:** Tuned `HorizontalPager` in `MainContent.kt` to `beyondViewportPageCount = 1`, reducing background composition overhead and memory consumption across multiple open tabs.
   * **Extra Keys Responsiveness:** Synchronized `ExtraKeys.kt` state keys with `canUndo`, `canRedo`, and `editable` properties for real-time virtual key activation.

5. **Container File Manager Overhaul (`ContainerFileManagerSheet.kt`):**
   * **Segmented, Ergonomic Action Layout:** Redesigned toolbar with primary elevated "Open in Workspace" button, "+ File", "+ Folder", inline paste, sort/filter menu, and multi-select mode toggle.
   * **Direct Terminal Shortcut:** Added one-click "Open in Terminal" button in header, toolbar, and directory context menu that automatically opens `Terminal` with `cd '$currentPath'`.
   * **Linux Permissions & Owner Editor:** Added `chmod` dialog with quick presets (`644`, `755`, `777`, `600`, `+x`) and custom octal input, and `chown` dialog with container user chips.
   * **Clipboard & File Operations:** Implemented container clipboard (Copy, Cut, Paste, Duplicate) and instant path copying.
   * **Multi-Selection Batch Mode:** Supported batch selection with select-all, batch deletion, and batch permissions updates.
   * **Detailed File Properties Modal:** Displays exact size in bytes, octal & symbolic permissions, owner, group, last modified timestamp, and type.
   * **Syntax-Aware File Icons & Sorting:** Integrated `FileIcon` with icon pack support and comprehensive sorting (Name, Size, Date Modified, Hidden Files toggle).
   * **Layout Polish:** Fixed container chip vertical wrapping and directory item click path resolution.

6. **Terminal Intent & Directory Navigation Fix (`Terminal.kt` & `DroidspacesTerminalSessionManager.kt`):**
   * Implemented `onNewIntent()` handling in `Terminal.kt` for `singleTask` background recovery.
   * Added delayed command execution (`runCommandInSession`) to ensure container shell `/bin/bash` or `su` prompt attaches before sending `cd` commands.
   * Passed container name and working directory extras from Container File Manager to Terminal.

7. **Storage & Workspace Drawer Overhaul (`AddProjectSheet.kt` & `Drawer.kt`):**
   * Replaced system file picker SAF contract with direct Internal Storage (`/sdcard`) access and Container Storage file manager sheet.
   * Dynamic versioning `1.0.0.<run_number>` and package name migration (`com.xed.editor`).

---

### 📋 What's Next (Upcoming Priorities)
1. **Live Container & File Management Verification:**
   * Test folder navigation, batch file deletion, and direct workspace mounting in live Droidspaces container instances.
   * Test terminal opening on deeply nested subdirectories.
2. **LSP Diagnostics & Language Server Enhancements:**
   * Audit language server lifecycle events and process cleanup when closing tabs.
   * Optimize diagnostic tooltip popups and formatting providers for large multi-module projects.
3. **File Tree & Workspace Search Polish:**
   * Benchmark background indexing and search performance across large workspace directory trees.

---

## 🛠️ Key Directories & Architecture
* `core/main/src/main/java/com/rk/`
  * `activities/main/` — Main window, view models, tabs, and drawer host.
  * `droidspaces/` — Droidspaces container manager, file object wrappers, shell execution, user management.
  * `editor/` — Sora editor extensions, color scheme patches, themes, snippets, and intelligent features.
  * `file/` — Pluggable file abstraction layer (`FileObject`, `FileWrapper`, `UriWrapper`, `ZipFileObject`).
  * `tabs/editor/` — Editor tab implementation, Compose bindings (`CodeEditorCompose.kt`), state (`CodeEditorState.kt`), and extra keys.
* `features/terminal/src/main/java/com/rk/terminal/` — Terminal session management, Compose terminal screen, and virtual keys.
* `terminal-view/` & `terminal-emulator/` — High-performance terminal emulation and rendering engine.
* `soraX/` — Core code editor library module with TextMate and LSP integration.
