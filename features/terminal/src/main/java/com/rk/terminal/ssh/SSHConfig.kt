package com.rk.terminal.ssh

import com.rk.settings.Settings

/**
 * Encapsulates the SSH connection configuration.
 */
data class SSHConfig(
    val host: String,
    val port: Int = 22,
    val username: String,
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
            return SSHConfig(
                host = Settings.ssh_host.trim(),
                port = if (Settings.ssh_port in 1..65535) Settings.ssh_port else 22,
                username = Settings.ssh_username.trim(),
                authType = Settings.ssh_auth_type,
                password = SSHSecureStorage.getPassword(),
                privateKey = SSHSecureStorage.getPrivateKey(),
                passphrase = SSHSecureStorage.getKeyPassphrase(),
            )
        }
    }
}
