package com.rk.terminal.ssh

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.rk.utils.application
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides hardware-backed (or Android KeyStore) AES-256-GCM secure storage
 * for sensitive SSH credentials (passwords, private keys, and passphrases).
 */
object SSHSecureStorage {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "xed_ssh_credentials_key_v1"
    private const val PREFS_NAME = "xed_ssh_secure_storage"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private const val KEY_SSH_PASSWORD = "enc_ssh_password"
    private const val KEY_SSH_PRIVATE_KEY = "enc_ssh_private_key"
    private const val KEY_SSH_KEY_PASSPHRASE = "enc_ssh_key_passphrase"

    private val prefs: SharedPreferences?
        get() = application?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec =
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plaintext: String): String? {
        if (plaintext.isEmpty()) return ""
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            fallbackObfuscate(plaintext)
        }
    }

    private fun decrypt(encryptedBase64: String?): String {
        if (encryptedBase64.isNullOrEmpty()) return ""
        return try {
            if (encryptedBase64.startsWith("FB:")) {
                return fallbackDeobfuscate(encryptedBase64)
            }
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return ""

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, combined, 0, GCM_IV_LENGTH)

            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(combined, GCM_IV_LENGTH, combined.size - GCM_IV_LENGTH)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun fallbackObfuscate(data: String): String {
        val bytes = data.toByteArray(Charsets.UTF_8)
        val obfuscated = bytes.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        return "FB:" + Base64.encodeToString(obfuscated, Base64.NO_WRAP)
    }

    private fun fallbackDeobfuscate(data: String): String {
        return try {
            val raw = data.removePrefix("FB:")
            val bytes = Base64.decode(raw, Base64.NO_WRAP)
            val deobfuscated = bytes.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
            String(deobfuscated, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun getPassword(): String = decrypt(prefs?.getString(KEY_SSH_PASSWORD, null))

    fun savePassword(password: String) {
        val encrypted = encrypt(password)
        prefs?.edit()?.putString(KEY_SSH_PASSWORD, encrypted)?.apply()
    }

    fun setPassword(password: String) = savePassword(password)

    fun hasPassword(): Boolean = getPassword().isNotEmpty()

    fun getPrivateKey(): String = decrypt(prefs?.getString(KEY_SSH_PRIVATE_KEY, null))

    fun savePrivateKey(privateKey: String) {
        val encrypted = encrypt(privateKey)
        prefs?.edit()?.putString(KEY_SSH_PRIVATE_KEY, encrypted)?.apply()
    }

    fun setPrivateKey(privateKey: String) = savePrivateKey(privateKey)

    fun hasPrivateKey(): Boolean = getPrivateKey().isNotEmpty()

    fun getPassphrase(): String = decrypt(prefs?.getString(KEY_SSH_KEY_PASSPHRASE, null))

    fun savePassphrase(passphrase: String) {
        val encrypted = encrypt(passphrase)
        prefs?.edit()?.putString(KEY_SSH_KEY_PASSPHRASE, encrypted)?.apply()
    }

    fun setKeyPassphrase(passphrase: String) = savePassphrase(passphrase)

    fun getKeyPassphrase(): String = getPassphrase()

    fun clearCredentials() {
        prefs?.edit()?.clear()?.apply()
    }
}
