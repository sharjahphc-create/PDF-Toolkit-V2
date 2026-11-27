package com.example.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    /**
     * Migration 1 -> 2:
     * - Adds tts_sentence_index column to reading_progress for seamless TTS resume.
     * - Creates reading_stats table for local reading & TTS listening telemetry.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE reading_progress ADD COLUMN tts_sentence_index INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `reading_stats` (
                    `id` TEXT NOT NULL,
                    `date_iso` TEXT NOT NULL,
                    `document_id` TEXT NOT NULL,
                    `minutes_read` INTEGER NOT NULL,
                    `pages_read` INTEGER NOT NULL,
                    `tts_minutes_listened` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `device_id` TEXT NOT NULL,
                    `is_deleted` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`document_id`) REFERENCES `library_documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_reading_stats_document_id_date_iso` ON `reading_stats` (`document_id`, `date_iso`)"
            )
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
}
