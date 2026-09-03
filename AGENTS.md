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
   * Replaced system file picker SAF contract with direct Internal Storage access and Container Storage file manager sheet.
   * Dynamic versioning `1.0.0.<run_number>` and package name migration (`com.xed.editor`).

8. **Android Internal Storage File Manager (`InternalFileManagerSheet.kt`):**
   * Built a full-featured in-app file browser and manager for Android's internal storage (`/sdcard`) matching all container file manager capabilities.
   * Quick jump chips (`/sdcard`, `Download`, `Documents`, `DCIM`, `Pictures`, `Music`, `Movies`, `Android`), full breadcrumbs navigation, elevated "Open in Workspace" drawer tab mounting, "+ File" and "+ Folder" creation, clipboard copy/cut/paste, sorting & hidden files toggles, batch multi-selection deletion, real-time search, item context actions (Terminal, Copy, Cut, Duplicate, Rename, Properties, Delete), and direct file opening into editor tabs.

9. **Markdown Previewer Anchor & Outline Navigation (`MarkdownScrollController.kt`, `MarkdownRenderer.kt`, `MarkdownTab.kt` & `MarkdownBlockParser.kt`):**
   * Built high-performance `MarkdownScrollController` tracking dynamic runtime layout coordinates of headings, paragraph HTML anchors (`<a id="...">`, `<span id="...">`), custom header attributes (`{#custom-id}`), and footnotes.
   * Supported instant in-page topic redirection for markdown anchor links (e.g. `[What is Droidspaces?](#what-is-droidspaces)`, `[Non-GKI](#non-GKI)`, `[Security Philosophy](#security-model)`, `[Contributing](#contribution)`) with exact, prefix, suffix, and multi-word fuzzy matching.
   * Enabled cross-file and intra-file anchor navigation (e.g. `file.md#section` or `[[#section]]`) with `initialAnchor` deferred scroll handling.
   * Fixed Document Outline dialog (`OutlineDialog`) item clicking to smoothly scroll directly to the selected heading in the document.
   * Added footnote reference click support (`[^1]`) jumping directly to definition blocks.

10. **Internal Storage Terminal Navigation Fix (`InternalFileManagerSheet.kt`, `Terminal.kt` & `DroidspacesTerminalSessionManager.kt`):**
    * Updated "Open in Terminal" in Internal Storage file manager (header button and folder context menu) to open within Droidspaces Linux container terminal instead of Android root host shell.
    * Added fallback path normalization (`/storage/emulated/0/...` $\leftrightarrow$ `/sdcard/...`) to ensure accurate directory navigation inside containers.
    * Enhanced `DroidspacesTerminalSessionManager.getOrCreateSession` to distinguish session types, preventing container commands from being dispatched to Android root sessions and vice-versa.

11. **File Manager Scroll Jitter & Bottom Sheet Shaking Fix (`InternalFileManagerSheet.kt` & `ContainerFileManagerSheet.kt`):**
    * Fixed violent shaking and oscillation when scrolling or swiping up in file managers inside `ModalBottomSheet`.
    * Implemented custom `NestedScrollConnection` on file list `LazyColumn` components that absorbs unconsumed vertical overscroll delta and post-fling velocity, preventing nested scroll fighting between `LazyColumn` and `ModalBottomSheetState`.
    * Connected `rememberLazyListState` and path change auto-reset (`scrollToItem(0)`) for smooth folder transitions.

12. **Full-Page File Manager & Coroutine Deadlock Fix (`InternalFileManagerSheet.kt` & `ContainerFileManagerSheet.kt`):**
    * Fixed infinite loading spinner bug caused by calling `lazyListState.scrollToItem(0)` before `loadDirectory()`, which suspended indefinitely while the list was not yet in composition.
    * Converted both Internal Storage and Container File Managers from partial floating `ModalBottomSheet` into clean, edge-to-edge **Full-Page screens** using `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false))` with system bars insets and a dedicated back button.

13. **Markdown Previewer Smooth Scrolling & Performance Optimizations (`MarkdownTab.kt`, `InlineMarkdown.kt`, `LaTeXParser.kt` & `MarkdownScrollController.kt`):**
    * Replaced touch-intercepting raw pointer gesture loops with Compose `transformable`, giving 1-finger vertical scrolling full native priority and smooth fling inertia.
    * Converted `graphicsLayer` to the draw-only lambda modifier, bypassing recomposition and layout passes of the entire Markdown tree during zoom, pan, and scroll.
    * Implemented thread-safe LRU caches in `InlineMarkdown` and `LaTeXParser`, eliminating CommonMark AST parsing, regex evaluation, and string allocations on every scroll frame.
    * Removed `mutableStateOf` from coordinate tracking in `MarkdownScrollController` to eliminate scroll-time recomposition invalidations.

14. **Project Multi-Selection & Batch Deletion Fix (`FileTree.kt`, `FileActionDialogs.kt` & `FileTreeViewModel.kt`):**
    * Fixed bug where selecting multiple files in the project drawer tree and tapping Delete only deleted the first selected file.
    * Made `SelectionActions` in `FileTree.kt` receive the reactive `selectedFiles` list state as a parameter so Compose properly recomposes and updates the toolbar/dropdown action list when items are added to or removed from multi-selection.
    * Fixed single vs multi-file action filtering to correctly dynamically present bulk actions (Delete, Copy, Cut, Refresh) when multiple files are selected.
    * Improved batch deletion in `FileActionDialogs.kt` to safely execute batch file deletion, safely close matching open editor tabs using `tabManager.removeTab(tab)` without index shifting, and batch-refresh parent folder caches.
    * Guarded `selectFile` in `FileTreeViewModel.kt` against duplicate additions.

15. **Terminal New Tab Default User Fix (`TerminalScreen.kt`):**
    * Fixed bug where tapping the `+` button in terminal prompted for user selection even when a default terminal user was configured in settings.
    * Updated `launchUserSelectionOrSession` to automatically use the configured default terminal user when spawning new tabs (`isNewTab = true`), only presenting the user selection sheet if the default user is set to "Ask every time" (`""`).

16. **App-Wide Performance & Memory Optimizations:**
    * **O(1) FileType Resolution (`BuiltinFileType.kt`):** Replaced linear registry iteration and repetitive list allocations with precomputed concurrent hash maps (`extensionMap`, `nameMap`, `markdownMap`, `scopeMap`) in `FileTypeManager`, making file type and syntax scope lookups instantaneous $O(1)$ with zero allocations.
    * **File Icon Memoization (`FileIcon.kt`):** Added thread-safe `builtInIconCache` for built-in file icons and reordered file system checks to verify `file.isDirectory()` prior to `file.isFile()`, avoiding unnecessary stat/I/O operations during tree rendering.
    * **Markdown Syntax Highlighter Caching (`CodeHighlighter.kt`):** Implemented `highlighterCache` mapping TextMate scopes to `(TextMateLanguage, ColorScheme)` pairs, eliminating redundant language parser and theme re-instantiations on every rendered code block.
    * **Regex & Map Allocation Pruning in Markdown/LaTeX (`InlineMarkdown.kt` & `LaTeXParser.kt`):** Pre-compiled regular expressions (`OBSIDIAN_COMMENT_REGEX`, `SAMP_REGEX`, `VAR_REGEX`, `TAG_STRIP_REGEX`) and extracted mathematical character mapping tables (`BLACKBOARD_BOLD_MAP`, `CALLIGRAPHIC_MAP`, `FRAKTUR_MAP`, `SUPERSCRIPT_MAP`) as static singletons, eliminating thousands of runtime allocations during parsing and scrolling.
    * **Zero-Copy Line Streaming in Code Search & Indexer (`CodeSearchDirect.kt` & `ProjectIndexer.kt`):** Bypassed unnecessary `chunked()` list allocations for standard-sized lines during recursive direct search and background Room database indexing.
    * **Safe Buffer Sampling (`SearchUtils.kt`):** Guarded stream char sampling in `isFileSearchable()` against negative buffer lengths and empty file exceptions.

17. **Heavy Project Indexing & Room Database Crash Prevention (`ProjectIndexer.kt` & `IndexDatabase.kt`):**
    * **Stack-Safe Iterative Traversal:** Converted recursive directory walking to an iterative `ArrayDeque` work queue with maximum depth bounding (64 levels) and visited directory cycle detection (`visitedDirs`), preventing `StackOverflowError` and infinite loops from symlinks or circular mounts.
    * **Flat Heap Memory Management:** Implemented periodic batch insertion (500 items) for both `FileMeta` and `CodeLine` during directory crawling, preventing unbounded in-memory collection growth and `OutOfMemoryError` on projects with 50k+ files.
    * **Room Database Table Indexing:** Added composite indexes on `CodeLine.path` and `FileMeta.fileName`, turning $O(N)$ full table scans during file updates/deletions into $O(\log N)$ B-tree lookups.
    * **Chunked Batch Deletions:** Replaced single-item deletion loops with batch `deleteByPaths(chunk)` queries, preventing SQLite parameter overflows and transaction lock timeouts.
    * **Migration Resilience:** Enabled `fallbackToDestructiveMigration()` on `IndexDatabase` for clean automatic recovery on schema upgrades.

18. **Code Autocomplete & Suggestion Dialog Overhaul (`EditorAutoCompletion.java`, `DefaultCompletionLayout.java`, `DefaultCompletionItemAdapter.java`, `SnippetManager.kt`, `comparators.kt` & `KeywordManager.kt`):**
    * **Typing Debounce Protection:** Resolved critical bug where typing faster than 70ms between keystrokes dropped completion requests entirely without scheduling delayed execution. Replaced with an adaptive debounce runnable ensuring suggestions trigger reliably when typing pauses.
    * **Instant Snappy Display:** Replaced artificial 70ms lifecycle post delay in `show()` with 16ms (single vsync frame) for instantaneous, lag-free popup presentation.
    * **Smart Bi-Directional Dialog Placement:** Implemented viewport-aware positioning that flips the autocomplete dialog above the cursor line when space below is cramped (<120dp) by mobile soft keyboards, and added right-edge margin boundaries to stop popup truncation and premature dismissal.
    * **Eliminated Janky MotionEvent Loop:** Removed legacy while-loop in `DefaultCompletionLayout` that dispatched synthetic touch events up to 100 times during list navigation; replaced with native $O(1)$ ListView selection positioning, eliminating scroll lag and ANR risks.
    * **Adapter Performance & ViewHolder:** Replaced repetitive 4x `findViewById` on every row scroll with `ViewHolder` caching in `DefaultCompletionItemAdapter`.
    * **Kind Icon Fallback & Material 3 Styling:** Added automatic kind icon badge rendering (`SimpleCompletionIconDrawer.draw(kind)`) for Snippets, Keywords, and Identifiers, rounded selection highlights (6dp), 12dp card corner radius with 8dp elevation shadow on `DefaultCompletionLayout`, monospaced typography, and removed obsolete placeholder assets.
    * **Operator Delimiter & Prefix Bug Fix:** Fixed prefix computation in `SnippetManager` where operators like `=`, `+`, `*`, `[`, `{` were erroneously included in autocomplete prefixes (e.g. `x=for` -> `=for`), breaking snippet and keyword matching.
    * **Exact Match Priority & Multi-Language Snippets:** Prioritized exact matches (`"00_"`, `"10_"`, `"20_"`) over partial matches in sorting. Added built-in Java and C/C++ snippet packs, comprehensive scope aliasing, and prefix label highlight support in `highlightMatchLabel`.

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
