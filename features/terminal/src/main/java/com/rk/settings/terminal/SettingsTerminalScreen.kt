package com.rk.settings.terminal

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.rk.components.PreferenceList
import com.rk.components.RoundedValueSlider
import com.rk.components.SettingsItem
import com.rk.components.SingleInputDialog
import com.rk.components.SteppedValueSlider
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.terminal.ssh.SSHConfig
import com.rk.terminal.ssh.SSHConnection
import com.rk.terminal.ssh.SSHSecureStorage
import com.rk.utils.LoadingPopup
import com.rk.utils.dialogRes
import com.rk.utils.toast
import com.termux.terminal.TerminalEmulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

@Composable
fun SettingsTerminalScreen(overrideNavController: NavController? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showHostDialog by remember { mutableStateOf(false) }
    var hostValue by remember { mutableStateOf(Settings.ssh_host) }

    var showPortDialog by remember { mutableStateOf(false) }
    var portValue by remember { mutableStateOf(Settings.ssh_port.toString()) }

    var showUsernameDialog by remember { mutableStateOf(false) }
    var usernameValue by remember { mutableStateOf(Settings.ssh_username) }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordValue by remember { mutableStateOf("") }

    var showKeyDialog by remember { mutableStateOf(false) }
    var keyValue by remember { mutableStateOf("") }

    var showPassphraseDialog by remember { mutableStateOf(false) }
    var passphraseValue by remember { mutableStateOf("") }

    var hasPassword by remember { mutableStateOf(SSHSecureStorage.hasPassword()) }
    var hasPrivateKey by remember { mutableStateOf(SSHSecureStorage.hasPrivateKey()) }

    if (showHostDialog) {
        SingleInputDialog(
            title = stringResource(strings.ssh_host),
            inputLabel = stringResource(strings.ssh_host),
            inputValue = hostValue,
            onInputValueChange = { hostValue = it },
            onConfirm = {
                Settings.ssh_host = hostValue.trim()
            },
            onFinish = { showHostDialog = false },
        )
    }

    if (showPortDialog) {
        SingleInputDialog(
            title = stringResource(strings.ssh_port),
            inputLabel = stringResource(strings.ssh_port),
            inputValue = portValue,
            onInputValueChange = { portValue = it },
            onConfirm = {
                val port = portValue.toIntOrNull() ?: 22
                Settings.ssh_port = if (port in 1..65535) port else 22
            },
            onFinish = { showPortDialog = false },
        )
    }

    if (showUsernameDialog) {
        SingleInputDialog(
            title = stringResource(strings.ssh_username),
            inputLabel = stringResource(strings.ssh_username),
            inputValue = usernameValue,
            onInputValueChange = { usernameValue = it },
            onConfirm = {
                Settings.ssh_username = usernameValue.trim()
            },
            onFinish = { showUsernameDialog = false },
        )
    }

    if (showPasswordDialog) {
        SingleInputDialog(
            title = stringResource(strings.ssh_password),
            inputLabel = stringResource(strings.ssh_password),
            inputValue = passwordValue,
            isPassword = true,
            onInputValueChange = { passwordValue = it },
            onConfirm = {
                if (passwordValue.isNotEmpty()) {
                    SSHSecureStorage.savePassword(passwordValue)
                    hasPassword = true
                }
            },
            onFinish = { showPasswordDialog = false },
        )
    }

    if (showKeyDialog) {
        SingleInputDialog(
            title = stringResource(strings.ssh_private_key),
            inputLabel = "-----BEGIN OPENSSH PRIVATE KEY-----...",
            inputValue = keyValue,
            singleLineMode = false,
            onInputValueChange = { keyValue = it },
            onConfirm = {
                if (keyValue.isNotBlank()) {
                    SSHSecureStorage.savePrivateKey(keyValue.trim())
                    hasPrivateKey = true
                }
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
            onInputValueChange = { passphraseValue = it },
            onConfirm = {
                SSHSecureStorage.savePassphrase(passphraseValue)
            },
            onFinish = { showPassphraseDialog = false },
        )
    }

    PreferenceLayout(label = stringResource(id = strings.terminal), backArrowVisible = true) {
        PreferenceGroup(heading = stringResource(strings.ssh_configuration)) {
            SettingsItem(
                label = stringResource(strings.ssh_host),
                description = if (Settings.ssh_host.isNotBlank()) Settings.ssh_host else stringResource(strings.ssh_host_desc),
                showSwitch = false,
                default = false,
                onClick = {
                    hostValue = Settings.ssh_host
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
                    showUsernameDialog = true
                },
            )

            var authType by remember { mutableStateOf(Settings.ssh_auth_type) }
            PreferenceList(
                label = stringResource(strings.ssh_auth_type),
                description = if (authType == "key") stringResource(strings.ssh_auth_key) else stringResource(strings.ssh_auth_password),
                items = listOf(
                    "password" to strings.ssh_auth_password.getString(),
                    "key" to strings.ssh_auth_key.getString(),
                ),
                selectedItem = authType,
                onItemSelected = {
                    authType = it
                    Settings.ssh_auth_type = it
                },
            )

            if (authType == "password") {
                SettingsItem(
                    label = stringResource(strings.ssh_password),
                    description = if (hasPassword) stringResource(strings.ssh_password_set) else stringResource(strings.ssh_password_not_set),
                    showSwitch = false,
                    default = false,
                    onClick = {
                        passwordValue = ""
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
                        keyValue = SSHSecureStorage.getPrivateKey()
                        showKeyDialog = true
                    },
                )

                SettingsItem(
                    label = stringResource(strings.ssh_key_passphrase),
                    description = stringResource(strings.ssh_key_passphrase_desc),
                    showSwitch = false,
                    default = false,
                    onClick = {
                        passphraseValue = SSHSecureStorage.getPassphrase()
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
                    val config = SSHConfig.loadFromSettings()
                    if (!config.isConfigured()) {
                        toast(strings.ssh_missing_config)
                        return@SettingsItem
                    }

                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            SSHConnection.testConnection(config)
                        }

                        result.onSuccess { banner ->
                            dialogRes(
                                title = strings.ssh_connection_success.getString(),
                                msg = banner,
                            )
                        }.onFailure { err ->
                            dialogRes(
                                title = strings.ssh_connection_failed.getString().format(""),
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
