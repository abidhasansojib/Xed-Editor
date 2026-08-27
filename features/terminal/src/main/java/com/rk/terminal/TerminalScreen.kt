package com.rk.terminal

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rk.activities.settings.SettingsActivity
import com.rk.activities.settings.SettingsRoutes
import com.rk.activities.terminal.Terminal
import com.rk.components.SingleInputDialog
import com.rk.droidspaces.DroidspacesConstants
import com.rk.droidspaces.DroidspacesManager
import com.rk.droidspaces.SelectUserSheet
import com.rk.editor.FontCache
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.terminal.virtualkeys.VirtualKeysConstants
import com.rk.terminal.virtualkeys.VirtualKeysInfo
import com.rk.terminal.virtualkeys.VirtualKeysListener
import com.rk.terminal.virtualkeys.VirtualKeysView
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(terminalActivity: Terminal, initialCommand: String? = null) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val sessionList by DroidspacesTerminalSessionManager.sessionList.collectAsState()
    val currentSessionId by DroidspacesTerminalSessionManager.currentSessionId.collectAsState()

    var isTerminalReady by remember { mutableStateOf(DroidspacesTerminalSessionManager.hasActiveSessions()) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var sessionToRename by remember { mutableStateOf("") }
    var renameValue by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }

    var showUserPicker by remember { mutableStateOf(false) }
    var isCreatingNewTab by remember { mutableStateOf(false) }

    val containerName = remember {
        Settings.droidspaces_container_name.ifBlank { DroidspacesConstants.DEFAULT_CONTAINER_NAME }
    }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()

    val drawerWidth = 320.dp

    fun launchUserSelectionOrSession(isNewTab: Boolean) {
        val defaultUser = Settings.droidspaces_terminal_default_user.trim()
        if (defaultUser.isNotEmpty() && !isNewTab) {
            val client = TerminalBackEnd()
            val isAndroidRoot = defaultUser == DroidspacesConstants.ANDROID_ROOT_USER
            DroidspacesTerminalSessionManager.getOrCreateSession(
                context = context,
                client = client,
                containerName = if (isAndroidRoot) DroidspacesConstants.ANDROID_CONTAINER_NAME else containerName,
                user = defaultUser,
                initialCommand = initialCommand,
            )
            isTerminalReady = true
            terminalActivity.changeSession(DroidspacesTerminalSessionManager.currentSessionId.value)
        } else {
            isCreatingNewTab = isNewTab
            showUserPicker = true
        }
    }

    LaunchedEffect(Unit) {
        if (!DroidspacesTerminalSessionManager.hasActiveSessions()) {
            launchUserSelectionOrSession(isNewTab = false)
        }
    }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    if (showUserPicker) {
        SelectUserSheet(
            containerName = containerName,
            title = stringResource(strings.select_terminal_user),
            includeAndroidRoot = true,
            onDismiss = {
                showUserPicker = false
                if (!isTerminalReady && !DroidspacesTerminalSessionManager.hasActiveSessions()) {
                    terminalActivity.finish()
                }
            },
            onUserSelected = { username ->
                showUserPicker = false
                val client = TerminalBackEnd()
                val isAndroidRoot = username == DroidspacesConstants.ANDROID_ROOT_USER
                val targetContainer = if (isAndroidRoot) DroidspacesConstants.ANDROID_CONTAINER_NAME else containerName
                if (isCreatingNewTab) {
                    DroidspacesTerminalSessionManager.createNewTabSession(
                        context = context,
                        client = client,
                        containerName = targetContainer,
                        user = username,
                    )
                    terminalActivity.changeSession(DroidspacesTerminalSessionManager.currentSessionId.value)
                } else {
                    DroidspacesTerminalSessionManager.getOrCreateSession(
                        context = context,
                        client = client,
                        containerName = targetContainer,
                        user = username,
                        initialCommand = initialCommand,
                    )
                    isTerminalReady = true
                    terminalActivity.changeSession(DroidspacesTerminalSessionManager.currentSessionId.value)
                }
            },
        )
    }

    if (showRenameDialog) {
        SingleInputDialog(
            title = stringResource(strings.rename),
            inputLabel = stringResource(strings.name),
            inputValue = renameValue,
            errorMessage = renameError,
            onInputValueChange = {
                renameValue = it
                renameError =
                    if (it.isBlank()) {
                        strings.name_empty_err.getString()
                    } else if (it != sessionToRename && sessionList.contains(it)) {
                        strings.session_name_exists.getString()
                    } else null
            },
            onConfirm = {
                if (renameError == null && renameValue.isNotBlank() && renameValue != sessionToRename) {
                    DroidspacesTerminalSessionManager.renameSession(sessionToRename, renameValue)
                }
            },
            onFinish = { showRenameDialog = false },
        )
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(drawerWidth)) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = stringResource(strings.sessions), style = MaterialTheme.typography.titleLarge)
                        Row(horizontalArrangement = Arrangement.End) {
                            IconButton(
                                onClick = {
                                    launchUserSelectionOrSession(isNewTab = true)
                                    scope.launch { drawerState.close() }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(strings.add_session),
                                )
                            }

                            IconButton(
                                onClick = {
                                    val intent = Intent(context, SettingsActivity::class.java)
                                    intent.putExtra("route", SettingsRoutes.TerminalSettings.route)
                                    context.startActivity(intent)
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(strings.settings),
                                )
                            }
                        }
                    }

                    if (sessionList.isNotEmpty()) {
                        LazyColumn {
                            items(sessionList) { sessionId ->
                                val isSelected = sessionId == currentSessionId
                                val isAndroidRoot = DroidspacesTerminalSessionManager.isAndroidRootSession(sessionId)
                                NavigationDrawerItem(
                                    label = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                painter = painterResource(if (isAndroidRoot) drawables.android else drawables.terminal),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = if (isAndroidRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(text = sessionId)
                                        }
                                    },
                                    selected = isSelected,
                                    onClick = {
                                        terminalActivity.changeSession(sessionId)
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                                    badge = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    sessionToRename = sessionId
                                                    renameValue = sessionId
                                                    renameError = null
                                                    showRenameDialog = true
                                                },
                                                modifier = Modifier.size(24.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = stringResource(strings.rename),
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    if (isSelected) {
                                                        val index = sessionList.indexOf(sessionId)
                                                        val sessionBefore = sessionList.getOrNull(index - 1)
                                                        val sessionAfter = sessionList.getOrNull(index + 1)
                                                        val neighborSession = sessionBefore ?: sessionAfter
                                                        neighborSession?.let { terminalActivity.changeSession(it) }
                                                    }

                                                    DroidspacesTerminalSessionManager.terminateSession(sessionId)

                                                    if (DroidspacesTerminalSessionManager.sessionList.value.isEmpty()) {
                                                        terminalActivity.finish()
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = stringResource(strings.delete),
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val isAndroidRoot = DroidspacesTerminalSessionManager.isAndroidRootSession(currentSessionId)
                        val titleText = if (currentSessionId.isNotEmpty()) {
                            if (isAndroidRoot) {
                                val rootSessions = sessionList.filter { DroidspacesTerminalSessionManager.isAndroidRootSession(it) }
                                if (rootSessions.size > 1) {
                                    val index = currentSessionId.substringAfterLast('#', "").trim()
                                    if (index.isNotEmpty()) {
                                        "${stringResource(strings.android_root_shell)} #$index"
                                    } else {
                                        stringResource(strings.android_root_shell)
                                    }
                                } else {
                                    stringResource(strings.android_root_shell)
                                }
                            } else {
                                "$containerName: $currentSessionId"
                            }
                        } else {
                            stringResource(strings.droidspaces_terminal)
                        }
                        Text(text = titleText)
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                launchUserSelectionOrSession(isNewTab = true)
                            },
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(strings.add_session))
                        }

                        IconButton(
                            onClick = {
                                val intent = Intent(context, SettingsActivity::class.java)
                                intent.putExtra("route", SettingsRoutes.TerminalSettings.route)
                                context.startActivity(intent)
                            },
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = stringResource(strings.settings))
                        }
                    },
                )
            },
        ) { paddingValues ->
            val imePadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            val navPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val bottomPadding = if (imePadding > 0.dp) imePadding else navPadding

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding(), bottom = bottomPadding),
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (isTerminalReady && currentSessionId.isNotEmpty()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                TerminalView(ctx, null).apply {
                                    val client = TerminalBackEnd()
                                    val isAndroidRoot = DroidspacesTerminalSessionManager.isAndroidRootSession(currentSessionId)
                                    val targetContainer = if (isAndroidRoot) DroidspacesConstants.ANDROID_CONTAINER_NAME else containerName
                                    val targetUser = if (isAndroidRoot) DroidspacesConstants.ANDROID_ROOT_USER else null
                                    val session = DroidspacesTerminalSessionManager.getCurrentSession()
                                        ?: DroidspacesTerminalSessionManager.getOrCreateSession(
                                            context = ctx,
                                            client = client,
                                            containerName = targetContainer,
                                            user = targetUser,
                                            initialCommand = initialCommand,
                                        )

                                    attachSession(session)
                                    setTerminalViewClient(client)
                                    setTextSize(Settings.terminal_font_size)

                                    // Apply custom font if present
                                    val fontPath = Settings.terminal_font_path
                                    if (fontPath.isNotBlank()) {
                                        val typeface = FontCache.getTypeface(ctx, fontPath, Settings.is_terminal_font_asset)
                                        if (typeface != null) {
                                            setTypeface(typeface)
                                        }
                                    }

                                    applyTerminalColors(onSurfaceColor, surfaceColor)

                                    isFocusableInTouchMode = true
                                    requestFocus()
                                    keepScreenOn = true

                                    terminalActivity.terminalView = WeakReference(this)
                                }
                            },
                            update = { view ->
                                val currentSession = DroidspacesTerminalSessionManager.getCurrentSession()
                                if (currentSession != null && view.mTermSession != currentSession) {
                                    view.attachSession(currentSession)
                                }
                                val targetSize = Settings.terminal_font_size
                                if (view.mRenderer?.mTextSize != targetSize) {
                                    view.setTextSize(targetSize)
                                }
                                view.applyTerminalColors(onSurfaceColor, surfaceColor)
                            },
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                if (isTerminalReady) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        factory = { ctx ->
                            VirtualKeysView(ctx, null).apply {
                                val view = terminalActivity.terminalView.get()
                                val session = view?.mTermSession ?: DroidspacesTerminalSessionManager.getCurrentSession()
                                if (session != null) {
                                    virtualKeysViewClient = VirtualKeysListener(session)
                                }

                                setButtonColors(
                                    onSurfaceColor,
                                    0xFFf44336.toInt(),
                                    0x00000000,
                                    (0x33888888).toInt(),
                                )

                                val extraKeysJson = Settings.terminal_extra_keys
                                try {
                                    val extraKeysInfo = VirtualKeysInfo(
                                        extraKeysJson,
                                        "",
                                        VirtualKeysConstants.CONTROL_CHARS_ALIASES,
                                    )
                                    reload(extraKeysInfo)
                                } catch (_: Exception) {}

                                terminalActivity.virtualKeysView = WeakReference(this)
                            }
                        },
                        update = { vkv ->
                            val view = terminalActivity.terminalView.get()
                            val session = view?.mTermSession ?: DroidspacesTerminalSessionManager.getCurrentSession()
                            if (session != null) {
                                val currentListener = vkv.virtualKeysViewClient as? VirtualKeysListener
                                if (currentListener == null || currentListener.session != session) {
                                    vkv.virtualKeysViewClient = VirtualKeysListener(session)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun TerminalView.applyTerminalColors(onSurfaceColor: Int, surfaceColor: Int) {
    val emulator = mEmulator ?: return
    val colors = emulator.mColors ?: return
    val currentColors = colors.mCurrentColors ?: return
    val curFg = currentColors.getOrNull(TextStyle.COLOR_INDEX_FOREGROUND)
    val curBg = currentColors.getOrNull(TextStyle.COLOR_INDEX_BACKGROUND)
    val curCursor = currentColors.getOrNull(TextStyle.COLOR_INDEX_CURSOR)
    if (curFg == onSurfaceColor && curBg == surfaceColor && curCursor == onSurfaceColor) {
        return
    }
    colors.reset()
    currentColors[TextStyle.COLOR_INDEX_FOREGROUND] = onSurfaceColor
    currentColors[TextStyle.COLOR_INDEX_BACKGROUND] = surfaceColor
    currentColors[TextStyle.COLOR_INDEX_CURSOR] = onSurfaceColor
    onScreenUpdated()
    invalidate()
}
