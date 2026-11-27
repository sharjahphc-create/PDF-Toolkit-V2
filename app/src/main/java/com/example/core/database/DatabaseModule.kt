package com.example.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.database.dao.FeedbackDraftDao
import com.example.core.database.dao.LibraryDao
import com.example.core.database.dao.NoteDao
import com.example.core.database.dao.ReaderDao
import com.example.core.database.dao.ToolUsageDao
import com.example.core.database.migrations.DatabaseMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePdfToolkitDatabase(
        @ApplicationContext context: Context
    ): PdfToolkitDatabase {
        return Room.databaseBuilder(
            context,
            PdfToolkitDatabase::class.java,
            PdfToolkitDatabase.DATABASE_NAME
        )
            .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
            .addCallback(DefaultCategoriesCallback())
            .build()
    }

    @Provides
    fun provideLibraryDao(db: PdfToolkitDatabase): LibraryDao = db.libraryDao()

    @Provides
    fun provideReaderDao(db: PdfToolkitDatabase): ReaderDao = db.readerDao()

    @Provides
    fun provideToolUsageDao(db: PdfToolkitDatabase): ToolUsageDao = db.toolUsageDao()

    @Provides
    fun provideNoteDao(db: PdfToolkitDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideFeedbackDraftDao(db: PdfToolkitDatabase): FeedbackDraftDao = db.feedbackDraftDao()
}

private class DefaultCategoriesCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val now = System.currentTimeMillis()
        val defaults = listOf(
            CategorySeed("cat_work", "Work", "work", 0xFF2563EB, 0),
            CategorySeed("cat_personal", "Personal", "person", 0xFF0D9488, 1),
            CategorySeed("cat_bills", "Bills", "receipt", 0xFFD97706, 2),
            CategorySeed("cat_ids", "IDs", "badge", 0xFF7C3AED, 3),
            CategorySeed("cat_school", "School", "school", 0xFFDB2777, 4)
        )
        defaults.forEach { cat ->
            db.execSQL(
                """
                INSERT OR IGNORE INTO categories (
                    id, name, icon_name, color_hex, is_locked, is_system_default, sort_order, updated_at, device_id, is_deleted
                ) VALUES (?, ?, ?, ?, 0, 1, ?, ?, ?, 0)
                """.trimIndent(),
                arrayOf<Any>(cat.id, cat.name, cat.iconName, cat.colorHex, cat.sortOrder, now, UUID.randomUUID().toString())
            )
        }
    }

    private data class CategorySeed(
        val id: String,
        val name: String,
        val iconName: String,
        val colorHex: Long,
        val sortOrder: Int
    )
}
