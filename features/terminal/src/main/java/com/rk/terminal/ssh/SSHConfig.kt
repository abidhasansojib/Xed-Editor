package com.rk.terminal.ssh

import com.rk.settings.Settings

data class SSHConfig(
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val authType: String = "password", // "password" or "key"
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
) {
    fun isConfigured(): Boolean {
        return host.isNotBlank() && username.isNotBlank()
    }

    companion object {
        fun loadFromSettings(): SSHConfig {
            val authType = Settings.ssh_auth_type
            val password = if (authType == "password") SSHSecureStorage.getPassword() ?: "" else ""
            val privateKey = if (authType == "key") SSHSecureStorage.getPrivateKey() ?: "" else ""
            val passphrase = SSHSecureStorage.getPassphrase() ?: ""

            return SSHConfig(
                host = Settings.ssh_host.trim(),
                port = if (Settings.ssh_port > 0) Settings.ssh_port else 22,
                username = Settings.ssh_username.trim(),
                authType = authType,
                password = password,
                privateKey = privateKey,
                passphrase = passphrase,
            )
        }

        fun saveToSettings(config: SSHConfig) {
            Settings.ssh_host = config.host.trim()
            Settings.ssh_port = if (config.port > 0) config.port else 22
            Settings.ssh_username = config.username.trim()
            Settings.ssh_auth_type = config.authType

            if (config.password.isNotEmpty()) {
                SSHSecureStorage.savePassword(config.password)
            }
            if (config.privateKey.isNotEmpty()) {
                SSHSecureStorage.savePrivateKey(config.privateKey)
            }
            if (config.passphrase.isNotEmpty()) {
                SSHSecureStorage.savePassphrase(config.passphrase)
            }
        }
    }
}
