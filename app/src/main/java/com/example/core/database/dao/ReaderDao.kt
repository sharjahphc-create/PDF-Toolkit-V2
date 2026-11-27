package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.BookmarkEntity
import com.example.core.database.entity.HighlightEntity
import com.example.core.database.entity.ReadingProgressEntity
import com.example.core.database.entity.ReadingStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderDao {

    @Query("SELECT * FROM reading_progress WHERE document_id = :documentId AND is_deleted = 0 LIMIT 1")
    fun observeProgress(documentId: String): Flow<ReadingProgressEntity?>

    @Query("SELECT * FROM reading_progress WHERE document_id = :documentId AND is_deleted = 0 LIMIT 1")
    suspend fun getProgress(documentId: String): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: ReadingProgressEntity)

    @Query(
        """
        SELECT * FROM bookmarks
        WHERE document_id = :documentId AND is_deleted = 0
        ORDER BY page_number ASC, created_at ASC
        """
    )
    fun observeBookmarks(documentId: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: BookmarkEntity)

    @Query(
        """
        UPDATE bookmarks
        SET is_deleted = 1, updated_at = :updatedAt, device_id = :deviceId
        WHERE id = :bookmarkId
        """
    )
    suspend fun softDeleteBookmark(bookmarkId: String, updatedAt: Long, deviceId: String)

    @Query(
        """
        SELECT * FROM highlights
        WHERE document_id = :documentId AND is_deleted = 0
        ORDER BY page_number ASC, created_at ASC
        """
    )
    fun observeHighlights(documentId: String): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHighlight(highlight: HighlightEntity)

    @Query(
        """
        UPDATE highlights
        SET is_deleted = 1, updated_at = :updatedAt, device_id = :deviceId
        WHERE id = :highlightId
        """
    )
    suspend fun softDeleteHighlight(highlightId: String, updatedAt: Long, deviceId: String)

    @Query("SELECT * FROM reading_stats WHERE document_id = :documentId AND date_iso = :dateIso AND is_deleted = 0 LIMIT 1")
    suspend fun getDailyStats(documentId: String, dateIso: String): ReadingStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReadingStats(stats: ReadingStatsEntity)

    @Query("SELECT COALESCE(SUM(minutes_read), 0) FROM reading_stats WHERE is_deleted = 0")
    fun observeTotalMinutesRead(): Flow<Int>

    @Query("SELECT COALESCE(SUM(tts_minutes_listened), 0) FROM reading_stats WHERE is_deleted = 0")
    fun observeTotalTtsMinutes(): Flow<Int>

    @Query("SELECT * FROM reading_progress WHERE is_deleted = 0")
    suspend fun getAllProgressSync(): List<ReadingProgressEntity>

    @Query("SELECT * FROM bookmarks WHERE is_deleted = 0")
    suspend fun getAllBookmarksSync(): List<BookmarkEntity>

    @Query("SELECT * FROM highlights WHERE is_deleted = 0")
    suspend fun getAllHighlightsSync(): List<HighlightEntity>

    @Query("SELECT * FROM reading_stats WHERE is_deleted = 0")
    suspend fun getAllStatsSync(): List<ReadingStatsEntity>
}
