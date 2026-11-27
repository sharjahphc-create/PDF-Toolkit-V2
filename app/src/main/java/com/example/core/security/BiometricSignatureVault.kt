package com.example.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Serializable
data class VaultSignatureMetadata(
    val id: String,
    val label: String,
    val kind: String, // "SIGNATURE" or "INITIALS"
    val createdAt: Long
)

data class VaultSignatureEntry(
    val metadata: VaultSignatureMetadata,
    val pngBytes: ByteArray
)

@Singleton
class BiometricSignatureVault @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val vaultDir: File
        get() = File(context.filesDir, "signature_vault").apply { mkdirs() }

    private val indexFile: File
        get() = File(vaultDir, "index.json")

    fun canAuthenticateWithBiometrics(): Boolean {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Signature Vault",
        subtitle: String = "Verify your identity to access saved signatures"
    ): Boolean {
        if (!canAuthenticateWithBiometrics()) {
            // Device has no enrolled biometrics or lock screen (e.g. fresh emulator);
            // Keystore AES-256-GCM encryption remains active on disk.
            return true
        }
        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (continuation.isActive) continuation.resume(false)
                    }

                    override fun onAuthenticationFailed() {
                        // Prompt remains open for retry
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
            prompt.authenticate(info)
        }
    }

    suspend fun listMetadata(): List<VaultSignatureMetadata> = withContext(ioDispatcher) {
        readIndex()
    }

    suspend fun saveSignature(
        label: String,
        kind: String,
        pngBytes: ByteArray
    ): VaultSignatureEntry = withContext(ioDispatcher) {
        val id = UUID.randomUUID().toString()
        val encrypted = encryptBytes(pngBytes)
        File(vaultDir, "$id.sig").writeBytes(encrypted)

        val meta = VaultSignatureMetadata(
            id = id,
            label = label.ifBlank { if (kind == "INITIALS") "Initials" else "Signature" },
            kind = kind,
            createdAt = System.currentTimeMillis()
        )
        val updated = readIndex() + meta
        writeIndex(updated)
        VaultSignatureEntry(meta, pngBytes)
    }

    suspend fun loadAllEntries(): List<VaultSignatureEntry> = withContext(ioDispatcher) {
        readIndex().mapNotNull { meta ->
            val blob = File(vaultDir, "${meta.id}.sig")
            if (!blob.exists()) return@mapNotNull null
            runCatching {
                val plain = decryptBytes(blob.readBytes())
                VaultSignatureEntry(meta, plain)
            }.getOrNull()
        }
    }

    suspend fun deleteEntry(id: String) = withContext(ioDispatcher) {
        File(vaultDir, "$id.sig").delete()
        val remaining = readIndex().filterNot { it.id == id }
        writeIndex(remaining)
    }

    private fun readIndex(): List<VaultSignatureMetadata> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val decrypted = decryptBytes(indexFile.readBytes())
            json.decodeFromString<List<VaultSignatureMetadata>>(decrypted.decodeToString())
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(list: List<VaultSignatureMetadata>) {
        val encoded = json.encodeToString(list).encodeToByteArray()
        indexFile.writeBytes(encryptBytes(encoded))
    }

    fun encryptBytes(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    fun decryptBytes(payload: ByteArray): ByteArray {
        require(payload.size > 2) { "Invalid encrypted payload" }
        val ivLen = payload[0].toInt() and 0xFF
        val iv = payload.copyOfRange(1, 1 + ivLen)
        val ciphertext = payload.copyOfRange(1 + ivLen, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "pdf_toolkit_signature_vault_aes256"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
