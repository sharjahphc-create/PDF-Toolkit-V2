package com.example.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.core.database.dao.FeedbackDraftDao
import com.example.core.database.dao.LibraryDao
import com.example.core.database.dao.NoteDao
import com.example.core.database.dao.ReaderDao
import com.example.core.database.dao.ToolUsageDao
import com.example.core.database.entity.BookmarkEntity
import com.example.core.database.entity.CategoryEntity
import com.example.core.database.entity.CollectionEntity
import com.example.core.database.entity.DocumentCollectionCrossRef
import com.example.core.database.entity.DocumentFtsEntity
import com.example.core.database.entity.FeedbackDraftEntity
import com.example.core.database.entity.HighlightEntity
import com.example.core.database.entity.LibraryDocumentEntity
import com.example.core.database.entity.NoteEntity
import com.example.core.database.entity.ReadingProgressEntity
import com.example.core.database.entity.ReadingStatsEntity
import com.example.core.database.entity.ToolUsageEntity

@Database(
    entities = [
        CategoryEntity::class,
        LibraryDocumentEntity::class,
        DocumentFtsEntity::class,
        CollectionEntity::class,
        DocumentCollectionCrossRef::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        ReadingStatsEntity::class,
        ToolUsageEntity::class,
        NoteEntity::class,
        FeedbackDraftEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PdfToolkitDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun readerDao(): ReaderDao
    abstract fun toolUsageDao(): ToolUsageDao
    abstract fun noteDao(): NoteDao
    abstract fun feedbackDraftDao(): FeedbackDraftDao

    companion object {
        const val DATABASE_NAME = "pdf_toolkit.db"
    }
}
