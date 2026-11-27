package com.example.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultCryptoManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val secureRandom = SecureRandom()

    fun hashPin(pin: String): String {
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val hash = deriveKeyBytes(pin.toCharArray(), salt, PIN_ITERATIONS)
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        return "$saltB64:$hashB64"
    }

    fun verifyPin(pin: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return false
        val parts = storedHash.split(':')
        if (parts.size != 2) return false
        val salt = runCatching { Base64.decode(parts[0], Base64.NO_WRAP) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(parts[1], Base64.NO_WRAP) }.getOrNull() ?: return false
        val actual = deriveKeyBytes(pin.toCharArray(), salt, PIN_ITERATIONS)
        return MessageDigest.isEqual(expected, actual)
    }

    suspend fun encryptFileToVault(sourceFile: File, docId: String): Result<File> =
        withContext(ioDispatcher) {
            runCatching {
                val vaultDir = File(context.filesDir, "vault").apply { mkdirs() }
                val encryptedFile = File(vaultDir, "$docId.enc")
                val plainBytes = sourceFile.readBytes()
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateVaultMasterKey())
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(plainBytes)

                ByteArrayOutputStream().use { out ->
                    out.write(iv)
                    out.write(encryptedBytes)
                    encryptedFile.writeBytes(out.toByteArray())
                }
                encryptedFile
            }
        }

    suspend fun decryptVaultFileToTemp(encryptedFile: File, outputTempFile: File): Result<File> =
        withContext(ioDispatcher) {
            runCatching {
                val allBytes = encryptedFile.readBytes()
                require(allBytes.size > GCM_IV_BYTES) { "Corrupted encrypted vault file" }
                val iv = allBytes.copyOfRange(0, GCM_IV_BYTES)
                val ciphertext = allBytes.copyOfRange(GCM_IV_BYTES, allBytes.size)
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateVaultMasterKey(),
                    GCMParameterSpec(GCM_TAG_BITS, iv)
                )
                val plain = cipher.doFinal(ciphertext)
                outputTempFile.parentFile?.mkdirs()
                outputTempFile.writeBytes(plain)
                outputTempFile
            }
        }

    suspend fun encryptBackupBytes(plainPayload: ByteArray, passphrase: String): ByteArray =
        withContext(ioDispatcher) {
            require(passphrase.length >= 4) { "Passphrase must be at least 4 characters" }
            val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
            val keyBytes = deriveKeyBytes(passphrase.toCharArray(), salt, BACKUP_ITERATIONS)
            val key = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plainPayload)

            ByteArrayOutputStream().use { out ->
                out.write(BACKUP_MAGIC)
                out.write(salt)
                out.write(iv)
                out.write(ciphertext)
                out.toByteArray()
            }
        }

    suspend fun decryptBackupBytes(encryptedBackup: ByteArray, passphrase: String): Result<ByteArray> =
        withContext(ioDispatcher) {
            runCatching {
                val headerSize = BACKUP_MAGIC.size + SALT_BYTES + GCM_IV_BYTES
                require(encryptedBackup.size > headerSize) { "Invalid backup file" }
                val magic = encryptedBackup.copyOfRange(0, BACKUP_MAGIC.size)
                require(magic.contentEquals(BACKUP_MAGIC)) { "Unsupported backup file format" }

                var offset = BACKUP_MAGIC.size
                val salt = encryptedBackup.copyOfRange(offset, offset + SALT_BYTES)
                offset += SALT_BYTES
                val iv = encryptedBackup.copyOfRange(offset, offset + GCM_IV_BYTES)
                offset += GCM_IV_BYTES
                val ciphertext = encryptedBackup.copyOfRange(offset, encryptedBackup.size)

                val keyBytes = deriveKeyBytes(passphrase.toCharArray(), salt, BACKUP_ITERATIONS)
                val key = SecretKeySpec(keyBytes, "AES")
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.doFinal(ciphertext)
            }
        }

    private fun deriveKeyBytes(chars: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(chars, salt, iterations, 256)
        return factory.generateSecret(spec).encoded
    }

    private fun getOrCreateVaultMasterKey(): SecretKey {
        val ksResult = runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = ks.getKey(VAULT_KEY_ALIAS, null) as? SecretKey
            if (existing != null) return@runCatching existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                VAULT_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        }
        if (ksResult.isSuccess) {
            return ksResult.getOrThrow()
        }

        val fallbackFile = File(context.noBackupFilesDir, "vault_fallback.key")
        val keyBytes = if (fallbackFile.exists()) {
            fallbackFile.readBytes()
        } else {
            ByteArray(32).also {
                secureRandom.nextBytes(it)
                fallbackFile.parentFile?.mkdirs()
                fallbackFile.writeBytes(it)
            }
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val VAULT_KEY_ALIAS = "pdf_toolkit_vault_aes256_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val SALT_BYTES = 16
        private const val PIN_ITERATIONS = 30_000
        private const val BACKUP_ITERATIONS = 100_000
        private val BACKUP_MAGIC = "PDFTK1".toByteArray(Charsets.US_ASCII)
    }
}
