package com.rk.settings.terminal

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.rk.activities.settings.SettingsRoutes
import com.rk.activities.settings.settingsNavController
import com.rk.components.NextScreenCard
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.PreferenceList
import com.rk.components.RoundedValueSlider
import com.rk.components.SettingsItem
import com.rk.components.SingleInputDialog
import com.rk.components.SteppedValueSlider
import com.rk.droidspaces.ContainerStatus
import com.rk.droidspaces.ContainerUser
import com.rk.droidspaces.DroidspacesBinaryInstaller
import com.rk.droidspaces.DroidspacesConstants
import com.rk.droidspaces.DroidspacesManager
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.terminal.TerminalCursorStyle
import com.rk.utils.dialogRes
import com.rk.utils.toast
import com.termux.terminal.TerminalEmulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsTerminalScreen(overrideNavController: NavController? = null) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var containerName by remember {
        mutableStateOf(Settings.droidspaces_container_name.ifBlank { DroidspacesConstants.DEFAULT_CONTAINER_NAME })
    }
    var binaryPath by remember { mutableStateOf(Settings.droidspaces_binary_path) }
    var defaultTerminalUser by remember { mutableStateOf(Settings.droidspaces_terminal_default_user) }
    var defaultStorageUser by remember { mutableStateOf(Settings.droidspaces_storage_default_user) }

    var containerUsers by remember { mutableStateOf<List<ContainerUser>>(emptyList()) }
    var containerStatus by remember { mutableStateOf(ContainerStatus.NOT_FOUND) }
    var isRootAvailable by remember { mutableStateOf(false) }

    var showContainerNameDialog by remember { mutableStateOf(false) }
    var showBinaryPathDialog by remember { mutableStateOf(false) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var isInstallingBusybox by remember { mutableStateOf(false) }

    suspend fun refreshContainerInfo() {
        isRootAvailable = DroidspacesManager.checkRootAccess()
        containerStatus = DroidspacesManager.getContainerStatus(containerName)
        containerUsers = DroidspacesManager.getContainerUsers(containerName, useCache = false)
    }

    LaunchedEffect(containerName) {
        refreshContainerInfo()
    }

    if (showContainerNameDialog) {
        SingleInputDialog(
            title = stringResource(strings.container_name),
            inputLabel = stringResource(strings.container_name),
            inputValue = containerName,
            onInputValueChange = { containerName = it },
            onConfirm = {
                val clean = containerName.trim().ifBlank { DroidspacesConstants.DEFAULT_CONTAINER_NAME }
                containerName = clean
                Settings.droidspaces_container_name = clean
                scope.launch { refreshContainerInfo() }
            },
            onFinish = { showContainerNameDialog = false },
        )
    }

    if (showBinaryPathDialog) {
        SingleInputDialog(
            title = stringResource(strings.droidspaces_binary_path),
            inputLabel = stringResource(strings.droidspaces_binary_path),
            inputValue = binaryPath,
            onInputValueChange = { binaryPath = it },
            onConfirm = {
                val clean = binaryPath.trim().ifBlank { DroidspacesConstants.DEFAULT_DROIDSPACES_BINARY }
                binaryPath = clean
                Settings.droidspaces_binary_path = clean
                scope.launch { refreshContainerInfo() }
            },
            onFinish = { showBinaryPathDialog = false },
        )
    }

    PreferenceLayout(label = stringResource(id = strings.terminal), backArrowVisible = true) {
        PreferenceGroup(heading = stringResource(strings.droidspaces_configuration)) {
            SettingsItem(
                label = stringResource(strings.container_name),
                description = containerName,
                showSwitch = false,
                default = false,
                onClick = { showContainerNameDialog = true },
            )

            // Terminal Default User Dropdown (Always ask, root, auto-detected users)
            val terminalUserItems = mutableListOf(
                "" to strings.ask_every_time.getString(),
                "root" to strings.root_user.getString(),
            )
            containerUsers.filter { !it.isRoot }.forEach { u ->
                terminalUserItems.add(u.username to "${u.username} (${u.homeDir})")
            }

            val currentTerminalSelection = if (terminalUserItems.any { it.first == defaultTerminalUser }) {
                defaultTerminalUser
            } else ""

            val terminalUserDescription = when {
                defaultTerminalUser.isBlank() -> strings.ask_every_time.getString()
                defaultTerminalUser == "root" -> strings.root_user.getString()
                else -> defaultTerminalUser
            }

            PreferenceList(
                label = stringResource(strings.default_terminal_user),
                description = terminalUserDescription,
                items = terminalUserItems,
                selectedItem = currentTerminalSelection,
                onItemSelected = { selected ->
                    defaultTerminalUser = selected
                    Settings.droidspaces_terminal_default_user = selected
                },
            )

            // Storage Default User Dropdown (Always ask, root, auto-detected users)
            val storageUserItems = mutableListOf(
                "" to strings.ask_every_time.getString(),
                "root" to "root (/root)",
            )
            containerUsers.filter { !it.isRoot }.forEach { u ->
                storageUserItems.add(u.username to "${u.username} (${u.homeDir})")
            }

            val currentStorageSelection = if (storageUserItems.any { it.first == defaultStorageUser }) {
                defaultStorageUser
            } else ""

            val storageUserDescription = when {
                defaultStorageUser.isBlank() -> strings.ask_every_time.getString()
                defaultStorageUser == "root" -> "root (/root)"
                else -> defaultStorageUser
            }

            PreferenceList(
                label = stringResource(strings.default_storage_user),
                description = storageUserDescription,
                items = storageUserItems,
                selectedItem = currentStorageSelection,
                onItemSelected = { selected ->
                    defaultStorageUser = selected
                    Settings.droidspaces_storage_default_user = selected
                },
            )

            SettingsItem(
                label = stringResource(strings.droidspaces_binary_path),
                description = binaryPath,
                showSwitch = false,
                default = false,
                onClick = { showBinaryPathDialog = true },
            )

            val statusText = when (containerStatus) {
                ContainerStatus.RUNNING -> strings.container_running.getString()
                ContainerStatus.STOPPED -> strings.container_stopped.getString()
                ContainerStatus.NOT_FOUND -> strings.container_not_found.getString()
            }
            SettingsItem(
                label = stringResource(strings.container_status),
                description = "$statusText (Root: ${if (isRootAvailable) "Granted" else "Not available"})",
                showSwitch = false,
                default = false,
                onClick = { scope.launch { refreshContainerInfo() } },
            )

            SettingsItem(
                label = stringResource(strings.test_container),
                description = if (isTestingConnection) "Testing connection to container '$containerName'…"
                else stringResource(strings.test_container_desc),
                showSwitch = false,
                default = false,
                onClick = {
                    if (isTestingConnection) return@SettingsItem
                    isTestingConnection = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            DroidspacesManager.testContainer(containerName)
                        }
                        isTestingConnection = false

                        result.onSuccess { banner ->
                            dialogRes(
                                activity = activity,
                                title = "Droidspaces Connected",
                                msg = banner,
                            )
                        }.onFailure { err ->
                            dialogRes(
                                activity = activity,
                                title = "Connection Failed",
                                msg = err.localizedMessage ?: err.message ?: "Unknown error",
                            )
                        }
                    }
                },
            )

            SettingsItem(
                label = stringResource(strings.install_busybox),
                description = if (isInstallingBusybox) "Installing bundled BusyBox…"
                else stringResource(strings.install_busybox_desc),
                showSwitch = false,
                default = false,
                onClick = {
                    if (isInstallingBusybox) return@SettingsItem
                    isInstallingBusybox = true
                    scope.launch {
                        val result = DroidspacesBinaryInstaller.installBusybox(context)
                        isInstallingBusybox = false
                        result.onSuccess {
                            toast(strings.busybox_installed)
                        }.onFailure { err ->
                            dialogRes(
                                activity = activity,
                                title = strings.busybox_install_failed.getString(),
                                msg = err.localizedMessage ?: err.message ?: "Unknown error",
                            )
                        }
                    }
                },
            )
        }

        PreferenceGroup(heading = stringResource(strings.appearance)) {
            var cursorStyle by remember { mutableStateOf(TerminalCursorStyle.fromString(Settings.terminal_cursor_style)) }
            PreferenceList(
                label = stringResource(strings.cursor_style),
                description = stringResource(cursorStyle.stringRes),
                items = TerminalCursorStyle.entries.map { it to it.stringRes.getString() },
                selectedItem = cursorStyle,
                onItemSelected = {
                    cursorStyle = it
                    Settings.terminal_cursor_style = it.value
                },
            )

            SteppedValueSlider(
                label = stringResource(strings.text_size),
                description = stringResource(strings.text_size_desc),
                min = 9,
                max = 30,
                default = Settings.terminal_font_size,
                stepSize = 1,
            ) {
                Settings.terminal_font_size = it
            }

            NextScreenCard(
                label = stringResource(strings.manage_terminal_font),
                description = stringResource(strings.manage_terminal_font),
                navController = overrideNavController ?: settingsNavController.get(),
                route = SettingsRoutes.TerminalFontScreen,
            )

            NextScreenCard(
                label = stringResource(strings.change_extra_keys),
                description = stringResource(strings.change_extra_keys_desc),
                navController = overrideNavController ?: settingsNavController.get(),
                route = SettingsRoutes.TerminalExtraKeys,
            )

            SettingsItem(
                label = stringResource(strings.clipboard_keybindings),
                description = stringResource(strings.clipboard_keybindings_desc),
                default = Settings.terminal_clipboard_keybindings,
                sideEffect = { Settings.terminal_clipboard_keybindings = it },
            )

            RoundedValueSlider(
                label = stringResource(strings.scrollback_buffer),
                description = stringResource(strings.scrollback_buffer_desc),
                min = TerminalEmulator.TERMINAL_TRANSCRIPT_ROWS_MIN,
                max = TerminalEmulator.TERMINAL_TRANSCRIPT_ROWS_MAX,
                default = Settings.terminal_scrollback_buffer,
                stepSize = 5_000,
            ) {
                Settings.terminal_scrollback_buffer = it
            }

            SettingsItem(
                label = stringResource(strings.terminate_all_sessions),
                description = stringResource(strings.terminate_all_sessions_desc),
                default = Settings.terminate_sessions_on_exit,
                sideEffect = { Settings.terminate_sessions_on_exit = it },
            )
        }
    }
}
