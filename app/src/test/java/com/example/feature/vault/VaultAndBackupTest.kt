package com.example.feature.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.security.VaultCryptoManager
import com.example.data.repository.BackupCategoryRecord
import com.example.data.repository.BackupManifest
import com.example.data.repository.BackupSettingsRecord
import com.example.data.repository.BackupSignatureRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultAndBackupTest {

    @Test
    fun `pin hashing is salted and verifies accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = VaultCryptoManager(context, Dispatchers.IO)

        val hash1 = crypto.hashPin("4829")
        val hash2 = crypto.hashPin("4829")

        assertNotEquals("Each PIN hash must use a unique random salt", hash1, hash2)
        assertTrue(crypto.verifyPin("4829", hash1))
        assertTrue(crypto.verifyPin("4829", hash2))
        assertFalse(crypto.verifyPin("0000", hash1))
        assertFalse(crypto.verifyPin("4829", null))
        assertFalse(crypto.verifyPin("4829", "malformed"))
    }

    @Test
    fun `vault encrypts file to private vault with nomedia and decrypts cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = VaultCryptoManager(context, Dispatchers.IO)

        val plainBytes = "Confidential PDF content 12345".toByteArray(Charsets.UTF_8)
        val source = File(context.cacheDir, "secret_source.pdf").apply { writeBytes(plainBytes) }

        val encFile = crypto.encryptFileToVault(source, "doc-vault-test-1").getOrThrow()
        assertTrue(encFile.exists())
        assertTrue(encFile.name.endsWith(".enc"))
        assertTrue("Vault directory must contain .nomedia", File(encFile.parentFile, ".nomedia").exists())
        assertFalse("Ciphertext must not equal plaintext", encFile.readBytes().contentEquals(plainBytes))

        val decrypted = File(context.cacheDir, "decrypted_preview.pdf")
        crypto.decryptVaultFileToTemp(encFile, decrypted).getOrThrow()
        assertArrayEquals(plainBytes, decrypted.readBytes())

        source.delete()
        encFile.delete()
        decrypted.delete()
    }

    @Test
    fun `backup passphrase encryption round-trips and rejects wrong passphrase`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = VaultCryptoManager(context, Dispatchers.IO)

        val payload = "ZIP-archive-bytes-containing-notes-and-signatures".toByteArray(Charsets.UTF_8)
        val encrypted = crypto.encryptBackupBytes(payload, "StrongPass!2026")

        val decrypted = crypto.decryptBackupBytes(encrypted, "StrongPass!2026").getOrThrow()
        assertArrayEquals(payload, decrypted)

        val wrongPassResult = crypto.decryptBackupBytes(encrypted, "WrongPass!2026")
        assertTrue("Wrong passphrase must fail GCM authentication", wrongPassResult.isFailure)

        val tampered = encrypted.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0xFF).toByte() }
        val tamperedResult = crypto.decryptBackupBytes(tampered, "StrongPass!2026")
        assertTrue("Tampered ciphertext must fail GCM authentication", tamperedResult.isFailure)
    }

    @Test
    fun `backup manifest v2 serializes signatures settings and vault inclusion flag`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val manifest = BackupManifest(
            version = 2,
            exportedAt = 1_700_000_000_000L,
            includesVaultFiles = false,
            categories = listOf(
                BackupCategoryRecord(
                    id = "cat_taxes",
                    name = "Taxes",
                    iconName = "finance",
                    colorHex = 0xFF059669L,
                    isLocked = true,
                    isSystemDefault = false,
                    sortOrder = 6,
                    updatedAt = 1_700_000_000_000L,
                    deviceId = "dev-1"
                )
            ),
            documents = emptyList(),
            bookmarks = emptyList(),
            highlights = emptyList(),
            notes = emptyList(),
            signatures = listOf(
                BackupSignatureRecord(
                    id = "sig-1",
                    label = "Primary Signature",
                    kind = "SIGNATURE",
                    createdAt = 1_700_000_000_000L,
                    archiveEntryName = "signatures/sig-1.png"
                )
            ),
            settings = BackupSettingsRecord(
                themeMode = "DARK",
                hideLockedThumbnails = true,
                vaultAutoLockMinutes = 5
            )
        )

        val encoded = json.encodeToString(manifest)
        val decoded = json.decodeFromString<BackupManifest>(encoded)

        assertEquals(2, decoded.version)
        assertFalse(decoded.includesVaultFiles)
        assertEquals(1, decoded.signatures.size)
        assertEquals("Primary Signature", decoded.signatures.first().label)
        assertEquals("DARK", decoded.settings?.themeMode)
        assertEquals(5, decoded.settings?.vaultAutoLockMinutes)
    }
}
