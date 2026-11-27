package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.PdfToolkitDatabase
import com.example.core.database.entity.CategoryEntity
import com.example.core.database.entity.LibraryDocumentEntity
import com.example.core.database.migrations.DatabaseMigrations
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PDF Toolkit", appName)
    }

    @Test
    fun `room migration 1 to 2 adds tts_sentence_index and reading_stats`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_${System.nanoTime()}.db"
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `library_documents` (
                            `id` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `file_name` TEXT NOT NULL,
                            `mime_type` TEXT NOT NULL,
                            `source_uri` TEXT,
                            `local_cache_path` TEXT NOT NULL,
                            `page_count` INTEGER NOT NULL,
                            `file_size_bytes` INTEGER NOT NULL,
                            `category_id` TEXT,
                            `tags_json` TEXT NOT NULL,
                            `ocr_text` TEXT NOT NULL,
                            `thumbnail_path` TEXT,
                            `is_locked` INTEGER NOT NULL,
                            `is_favorite` INTEGER NOT NULL,
                            `last_opened_at` INTEGER NOT NULL,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            `device_id` TEXT NOT NULL,
                            `is_deleted` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `reading_progress` (
                            `id` TEXT NOT NULL,
                            `document_id` TEXT NOT NULL,
                            `current_page` INTEGER NOT NULL,
                            `total_pages` INTEGER NOT NULL,
                            `scroll_offset_fraction` REAL NOT NULL,
                            `last_read_duration_sec` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            `device_id` TEXT NOT NULL,
                            `is_deleted` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO library_documents VALUES (
                            'doc-1', 'Sample', 'sample.pdf', 'application/pdf', NULL, '/tmp/s.pdf',
                            2, 1024, NULL, '[]', 'hello', NULL, 0, 0, 100, 100, 100, 'dev-1', 0
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO reading_progress VALUES (
                            'prog-1', 'doc-1', 1, 2, 0.5, 30, 100, 'dev-1', 0
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        DatabaseMigrations.MIGRATION_1_2.migrate(db)

        db.query("SELECT tts_sentence_index FROM reading_progress WHERE id = 'prog-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='reading_stats'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `room fts search and soft delete work as expected`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PdfToolkitDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val dao = db.libraryDao()
        dao.upsertCategory(
            CategoryEntity(
                id = "cat_work",
                name = "Work",
                iconName = "work",
                colorHex = 0xFF2563EB,
                isSystemDefault = true
            )
        )
        dao.upsertDocument(
            LibraryDocumentEntity(
                id = "doc-100",
                title = "Quarterly Tax Summary",
                fileName = "tax_2026.pdf",
                mimeType = "application/pdf",
                localCachePath = "/tmp/tax_2026.pdf",
                pageCount = 5,
                fileSizeBytes = 4096,
                categoryId = "cat_work",
                ocrText = "Important offline invoice breakdown for Q4"
            )
        )

        val matched = dao.searchDocumentsFts("invoice*", includeLocked = false).first()
        assertEquals(1, matched.size)
        assertEquals("doc-100", matched.first().id)

        dao.softDeleteDocument("doc-100", System.currentTimeMillis(), "device-1")
        val afterDelete = dao.observeDocuments(includeLocked = false).first()
        assertTrue(afterDelete.isEmpty())

        db.close()
    }
}
