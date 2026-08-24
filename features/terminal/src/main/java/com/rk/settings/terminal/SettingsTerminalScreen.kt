package com.rk.settings.terminal

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.rk.DocumentProvider
import com.rk.activities.main.MainActivity
import com.rk.activities.settings.SettingsActivity
import com.rk.activities.settings.SettingsRoutes
import com.rk.activities.settings.settingsNavController
import com.rk.components.NextScreenCard
import com.rk.components.PreferenceList
import com.rk.components.RoundedValueSlider
import com.rk.components.SettingsItem
import com.rk.components.SingleInputDialog
import com.rk.components.SteppedValueSlider
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.switch.PreferenceSwitch
import com.rk.feature.FeatureRegistry
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.localBinDir
import com.rk.file.localDir
import com.rk.file.localLibDir
import com.rk.file.sandboxDir
import com.rk.file.toFileObject
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.terminal.terminalView
import com.rk.utils.LoadingPopup
import com.rk.utils.dialogRes
import com.rk.utils.dpToPx
import com.rk.utils.getTempDir
import com.rk.utils.toast
import com.termux.terminal.TerminalEmulator
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.lang.Runtime.getRuntime

enum class TerminalCursorStyle(val value: String, val stringRes: Int) {
    BLOCK("block", strings.block),
    BAR("bar", strings.bar),
    UNDERLINE("underline", strings.underline);

    companion object {
        fun fromString(value: String): TerminalCursorStyle {
            return entries.firstOrNull { it.value == value } ?: BLOCK
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun SettingsTerminalScreen(overrideNavController: NavController? = null) {
    PreferenceLayout(label = stringResource(id = strings.terminal), backArrowVisible = true) {
        val context = LocalContext.current
        val activity = LocalActivity.current as? AppCompatActivity

        var showHostDialog by remember { mutableStateOf(false) }
        var hostValue by remember { mutableStateOf(Settings.ssh_host) }
        var hostError by remember { mutableStateOf<String?>(null) }

        var showPortDialog by remember { mutableStateOf(false) }
        var portValue by remember { mutableStateOf(Settings.ssh_port.toString()) }
        var portError by remember { mutableStateOf<String?>(null) }

        var showUsernameDialog by remember { mutableStateOf(false) }
        var usernameValue by remember { mutableStateOf(Settings.ssh_username) }
        var usernameError by remember { mutableStateOf<String?>(null) }

        var showPasswordDialog by remember { mutableStateOf(false) }
        var passwordValue by remember { mutableStateOf("") }

        var showKeyDialog by remember { mutableStateOf(false) }
        var keyValue by remember { mutableStateOf("") }

        var showPassphraseDialog by remember { mutableStateOf(false) }
        var passphraseValue by remember { mutableStateOf("") }

        var hasPassword by remember { mutableStateOf(com.rk.terminal.ssh.SSHSecureStorage.hasPassword()) }
        var hasPrivateKey by remember { mutableStateOf(com.rk.terminal.ssh.SSHSecureStorage.hasPrivateKey()) }

        PreferenceGroup(heading = stringResource(strings.use_ssh_terminal)) {
            var useSSHState by remember { mutableStateOf(Settings.use_ssh_terminal) }
            PreferenceSwitch(
                checked = useSSHState,
                onCheckedChange = {
                    Settings.use_ssh_terminal = it
                    useSSHState = it
                },
                label = stringResource(strings.use_ssh_terminal),
                description = stringResource(strings.use_ssh_terminal_desc),
            )

            if (useSSHState) {
                SettingsItem(
                    label = stringResource(strings.ssh_host),
                    description = if (Settings.ssh_host.isNotBlank()) Settings.ssh_host else stringResource(strings.ssh_host_desc),
                    showSwitch = false,
                    default = false,
                    onClick = {
                        hostValue = Settings.ssh_host
                        hostError = null
                        showHostDialog = true
                    },
                )

                SettingsItem(
                    label = stringResource(strings.ssh_port),
                    description = Settings.ssh_port.toString(),
                    showSwitch = false,
                    default = false,
                    onClick = {
                        portValue = Settings.ssh_port.toString()
                        portError = null
                        showPortDialog = true
                    },
                )

                SettingsItem(
                    label = stringResource(strings.ssh_username),
                    description = if (Settings.ssh_username.isNotBlank()) Settings.ssh_username else stringResource(strings.ssh_username_desc),
                    showSwitch = false,
                    default = false,
                    onClick = {
                        usernameValue = Settings.ssh_username
                        usernameError = null
                        showUsernameDialog = true
                    },
                )

                PreferenceList(
                    label = stringResource(strings.ssh_auth_type),
                    description = null,
                    items =
                        listOf(
                            "password" to stringResource(strings.ssh_auth_password),
                            "key" to stringResource(strings.ssh_auth_key),
                        ),
                    selectedItem = Settings.ssh_auth_type,
                    onItemSelected = { Settings.ssh_auth_type = it },
                )

                if (Settings.ssh_auth_type == "password") {
                    SettingsItem(
                        label = stringResource(strings.ssh_password),
                        description = if (hasPassword) stringResource(strings.ssh_password_set) else stringResource(strings.ssh_password_not_set),
                        showSwitch = false,
                        default = false,
                        onClick = {
                            passwordValue = com.rk.terminal.ssh.SSHSecureStorage.getPassword() ?: ""
                            showPasswordDialog = true
                        },
                    )
                } else {
                    SettingsItem(
                        label = stringResource(strings.ssh_private_key),
                        description = if (hasPrivateKey) stringResource(strings.ssh_private_key_set) else stringResource(strings.ssh_private_key_not_set),
                        showSwitch = false,
                        default = false,
                        onClick = {
                            keyValue = com.rk.terminal.ssh.SSHSecureStorage.getPrivateKey() ?: ""
                            showKeyDialog = true
                        },
                    )

                    SettingsItem(
                        label = stringResource(strings.ssh_key_passphrase),
                        description = stringResource(strings.ssh_key_passphrase_desc),
                        showSwitch = false,
                        default = false,
                        onClick = {
                            passphraseValue = com.rk.terminal.ssh.SSHSecureStorage.getKeyPassphrase() ?: ""
                            showPassphraseDialog = true
                        },
                    )
                }

                SettingsItem(
                    label = stringResource(strings.ssh_test_connection),
                    description = stringResource(strings.ssh_test_connection_desc),
                    showSwitch = false,
                    default = false,
                    onClick = {
                        val config = com.rk.terminal.ssh.SSHConfig.loadFromSettings()
                        if (!config.isConfigured()) {
                            toast(strings.ssh_missing_config)
                            return@SettingsItem
                        }

                        val loading = LoadingPopup(activity, null)
                        loading.show()

                        GlobalScope.launch(Dispatchers.IO) {
                            val result = com.rk.terminal.ssh.SSHConnection.testConnection(config)
                            withContext(Dispatchers.Main) {
                                loading.hide()
                                result.fold(
                                    onSuccess = { toast(strings.ssh_connection_success) },
                                    onFailure = {
                                        dialogRes(
                                            activity = activity,
                                            title = strings.error.getString(),
                                            msg = strings.ssh_connection_failed.getString().format(it.message ?: "Unknown error"),
                                            okRes = strings.ok,
                                        )
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }

        if (showHostDialog) {
            SingleInputDialog(
                title = stringResource(strings.ssh_host),
                inputLabel = stringResource(strings.ssh_host),
                inputValue = hostValue,
                errorMessage = hostError,
                confirmEnabled = hostValue.isNotBlank(),
                onInputValueChange = {
                    hostValue = it
                    hostError = if (it.isBlank()) strings.value_invalid.getString() else null
                },
                onConfirm = { Settings.ssh_host = hostValue.trim() },
                onFinish = { showHostDialog = false },
            )
        }

        if (showPortDialog) {
            SingleInputDialog(
                title = stringResource(strings.ssh_port),
                inputLabel = stringResource(strings.ssh_port),
                inputValue = portValue,
                errorMessage = portError,
                confirmEnabled = portValue.isNotBlank(),
                onInputValueChange = {
                    portValue = it
                    val port = it.toIntOrNull()
                    portError = if (port == null || port !in 1..65535) strings.invalid_port.getString() else null
                },
                onConfirm = { Settings.ssh_port = portValue.toInt() },
                onFinish = { showPortDialog = false },
            )
        }

        if (showUsernameDialog) {
            SingleInputDialog(
                title = stringResource(strings.ssh_username),
                inputLabel = stringResource(strings.ssh_username),
                inputValue = usernameValue,
                errorMessage = usernameError,
                confirmEnabled = usernameValue.isNotBlank(),
                onInputValueChange = {
                    usernameValue = it
                    usernameError = if (it.isBlank()) strings.value_invalid.getString() else null
                },
                onConfirm = { Settings.ssh_username = usernameValue.trim() },
                onFinish = { showUsernameDialog = false },
            )
        }

        if (showPasswordDialog) {
            SingleInputDialog(
                title = stringResource(strings.ssh_password),
                inputLabel = stringResource(strings.ssh_password),
                inputValue = passwordValue,
                isPassword = true,
                confirmEnabled = true,
                onInputValueChange = { passwordValue = it },
                onConfirm = {
                    com.rk.terminal.ssh.SSHSecureStorage.setPassword(passwordValue)
                    hasPassword = com.rk.terminal.ssh.SSHSecureStorage.hasPassword()
                },
                onFinish = { showPasswordDialog = false },
            )
        }

        if (showKeyDialog) {
            SingleInputDialog(
                title = stringResource(strings.ssh_private_key),
                inputLabel = stringResource(strings.ssh_private_key),
                inputValue = keyValue,
                singleLineMode = false,
                confirmEnabled = true,
                onInputValueChange = { keyValue = it },
                onConfirm = {
                    com.rk.terminal.ssh.SSHSecureStorage.setPrivateKey(keyValue.trim())
                    hasPrivateKey = com.rk.terminal.ssh.SSHSecureStorage.hasPrivateKey()
                },
                onFinish = { showKeyDialog = false },
            )
        }

        if (showPassphraseDialog) {
            SingleInputDialog(
                title = stringResource(strings.ssh_key_passphrase),
                inputLabel = stringResource(strings.ssh_key_passphrase),
                inputValue = passphraseValue,
                isPassword = true,
                confirmEnabled = true,
                onInputValueChange = { passphraseValue = it },
                onConfirm = { com.rk.terminal.ssh.SSHSecureStorage.setKeyPassphrase(passphraseValue) },
                onFinish = { showPassphraseDialog = false },
            )
        }

        PreferenceGroup(heading = stringResource(strings.advanced)) {
            if (Settings.use_ssh_terminal) {
                SettingsItem(
                    label = stringResource(strings.failsafe_mode),
                    description = stringResource(strings.ssh_proot_disabled_notice),
                    default = false,
                    showSwitch = false,
                    sideEffect = {},
                )
            } else {
                if (FeatureRegistry.isEnabled("debug_mode")) {
                    SettingsItem(
                        label = stringResource(strings.failsafe_mode),
                        description = stringResource(strings.failsafe_mode_desc),
                        default = !Settings.sandbox,
                        sideEffect = { Settings.sandbox = !it },
                    )
                }

                PreferenceList(
                    label = "SECCOMP",
                    description = stringResource(strings.seccomp_desc),
                    items =
                        listOf(
                            "unspecified" to stringResource(strings.seccomp_unspecified),
                            "no" to stringResource(strings.seccomp_no_seccomp),
                            "yes" to stringResource(strings.seccomp_seccomp),
                        ),
                    selectedItem = Settings.seccomp_mode,
                    onItemSelected = { Settings.seccomp_mode = it },
                )

                NextScreenCard(
                    label = stringResource(strings.terminal_health),
                    description = stringResource(strings.terminal_health_desc),
                    navController = overrideNavController ?: settingsNavController.get(),
                    route = SettingsRoutes.TerminalCheck,
                )
            }
        }

        PreferenceGroup(heading = stringResource(strings.appearance)) {
            SteppedValueSlider(
                label = stringResource(strings.text_size),
                min = 10,
                max = 20,
                default = Settings.terminal_font_size,
                onValueChanged = {
                    Settings.terminal_font_size = it
                    terminalView.get()?.setTextSize(dpToPx(it.toFloat(), context))
                },
            )

            NextScreenCard(
                label = stringResource(strings.manage_terminal_font),
                description = stringResource(strings.manage_terminal_font),
                navController = overrideNavController ?: settingsNavController.get(),
                route = SettingsRoutes.TerminalFontScreen,
            )

            PreferenceList(
                label = stringResource(strings.cursor_style),
                description = stringResource(strings.cursor_style_desc),
                items = TerminalCursorStyle.entries.map { it to stringResource(it.stringRes) },
                selectedItem = TerminalCursorStyle.fromString(Settings.terminal_cursor_style),
                onItemSelected = { Settings.terminal_cursor_style = it.value },
            )
        }

        PreferenceGroup(heading = stringResource(strings.user_data)) {
            if (Settings.use_ssh_terminal) {
                SettingsItem(
                    label = stringResource(strings.uninstall),
                    description = stringResource(strings.ssh_proot_disabled_notice),
                    default = false,
                    showSwitch = false,
                    sideEffect = {},
                )
            } else {
                val restore =
                    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    if (uri == null) {
                        return@rememberLauncherForActivityResult
                    }

                    val loading = LoadingPopup(activity, null)
                    loading.show()

                    GlobalScope.launch(Dispatchers.IO) {
                        val fileObject = uri.toFileObject(expectedIsFile = true)

                        val tempFile = getTempDir().child("terminal-backup.tar.gz")

                        try {
                            fileObject.getInputStream().use { inputStream ->
                                FileOutputStream(tempFile).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }

                            sandboxDir().deleteRecursively()
                            sandboxDir().mkdirs()

                            val result =
                                getRuntime().exec("tar -xf ${tempFile.absolutePath} -C ${sandboxDir()}").waitFor()
                            withContext(Dispatchers.Main) {
                                loading.hide()
                                if (result == 0) {
                                    toast(strings.success)
                                } else {
                                    toast(strings.failed)
                                }
                            }

                            localDir().child(".terminal_setup_ok_DO_NOT_REMOVE").createFileIfNot()
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                loading.hide()
                                toast("Error: ${e.message}")
                            }
                        }
                    }
                }

            SettingsItem(
                label = stringResource(strings.backup),
                description = stringResource(strings.terminal_backup),
                showSwitch = false,
                default = false,
                sideEffect = {
                    val fileManager =
                        if (SettingsActivity.instance != null) {
                            SettingsActivity.instance!!.fileManager
                        } else {
                            MainActivity.instance!!.fileManager
                        }

                    fileManager.createNewFile(
                        mimeType = "application/octet-stream",
                        title = "terminal-backup.tar.gz",
                    ) { fileObject ->
                        GlobalScope.launch(Dispatchers.IO) {
                            if (fileObject != null) {
                                val targetFile = getTempDir().child("terminal-backup.tar.gz")

                                val loading = LoadingPopup(activity, null)
                                loading.show()

                                try {
                                    val sandboxDir = sandboxDir().absolutePath
                                    val targetPath = targetFile.absolutePath

                                    val processBuilder =
                                        ProcessBuilder(
                                                "tar",
                                                "-czf",
                                                targetPath,
                                                ".",
                                                "--exclude=dev",
                                                "--exclude=sys",
                                                "--exclude=proc",
                                                "--exclude=system",
                                                "--exclude=apex",
                                                "--exclude=vendor",
                                                "--exclude=data",
                                                "--exclude=home",
                                                "--exclude=root",
                                                "--exclude=var/cache",
                                                "--exclude=var/tmp",
                                                "--exclude=lost+found",
                                                "--exclude=storage",
                                                "--exclude=system_ext",
                                                "--exclude=tmp",
                                                "--exclude=vendor",
                                                "--exclude=sdcard",
                                                "--exclude=storage",
                                            )
                                            .apply {
                                                directory(File(sandboxDir))
                                                redirectErrorStream(true)
                                            }

                                    processBuilder.start().waitFor()

                                    loading.hide()

                                    targetFile.inputStream().use { inputStream ->
                                        fileObject.getOutputStream(false).use { outputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        loading.hide()
                                        toast("Error: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                },
            )

            SettingsItem(
                label = stringResource(strings.restore),
                description = stringResource(strings.restore_terminal),
                showSwitch = false,
                default = false,
                sideEffect = { restore.launch("application/gzip") },
            )

            SettingsItem(
                label = stringResource(strings.uninstall),
                default = false,
                description = stringResource(strings.uninstall_terminal),
                showSwitch = false,
                sideEffect = {
                    dialogRes(
                        activity = activity,
                        title = strings.attention.getString(),
                        msg = strings.uninstall_terminal_warning.getString(),
                        onCancel = {},
                        okRes = strings.delete,
                        onOk = {
                            GlobalScope.launch(Dispatchers.IO) {
                                val loading = LoadingPopup(activity, null)
                                loading.show()
                                runCatching {
                                    localBinDir().deleteRecursively()
                                    localLibDir().deleteRecursively()
                                    sandboxDir().deleteRecursively()
                                    localDir().child(".terminal_setup_ok_DO_NOT_REMOVE").delete()
                                }
                                loading.hide()
                            }
                        },
                    )
                },
            )
            }
        }

        PreferenceGroup(heading = stringResource(strings.other)) {
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
                toast(strings.restart_required)
            }

            SettingsItem(
                label = stringResource(strings.terminate_all_sessions),
                description = stringResource(strings.terminate_all_sessions_desc),
                default = Settings.terminate_sessions_on_exit,
                sideEffect = { Settings.terminate_sessions_on_exit = it },
            )

            SettingsItem(
                label = stringResource(strings.project_as_wk),
                description = stringResource(strings.project_as_wk_desc),
                default = Settings.project_as_pwd,
                sideEffect = { Settings.project_as_pwd = it },
            )

            var exposeHomeDirState by remember { mutableStateOf(Settings.expose_home_dir) }
            PreferenceSwitch(
                checked = exposeHomeDirState,
                onCheckedChange = {
                    if (it) {
                        dialogRes(
                            activity = activity,
                            title = strings.attention.getString(),
                            msg = strings.saf_expose_warning.getString(),
                            okRes = strings.continue_action,
                            onCancel = {},
                            onOk = {
                                Settings.expose_home_dir = true
                                DocumentProvider.setDocumentProviderEnabled(context, true)
                                exposeHomeDirState = true
                            },
                        )
                    } else {
                        Settings.expose_home_dir = false
                        exposeHomeDirState = false
                        DocumentProvider.setDocumentProviderEnabled(context, false)
                    }
                },
                label = stringResource(strings.expose_saf),
                description = stringResource(strings.expose_saf_desc),
            )
        }
    }
}
