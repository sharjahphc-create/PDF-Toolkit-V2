package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.FeedbackDraftEntity
import com.example.core.database.entity.NoteEntity
import com.example.core.database.entity.ToolUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolUsageDao {
    @Query("SELECT * FROM tool_usage WHERE is_deleted = 0 ORDER BY favorite_order ASC, usage_count DESC")
    fun observeAllToolUsage(): Flow<List<ToolUsageEntity>>

    @Query("SELECT * FROM tool_usage WHERE tool_id = :toolId AND is_deleted = 0 LIMIT 1")
    suspend fun getToolUsage(toolId: String): ToolUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertToolUsage(usage: ToolUsageEntity)

    @Query("SELECT * FROM tool_usage WHERE is_deleted = 0")
    suspend fun getAllToolUsageSync(): List<ToolUsageEntity>
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE is_deleted = 0 ORDER BY is_pinned DESC, updated_at DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: NoteEntity)

    @Query("UPDATE notes SET is_deleted = 1, updated_at = :updatedAt, device_id = :deviceId WHERE id = :noteId")
    suspend fun softDeleteNote(noteId: String, updatedAt: Long, deviceId: String)

    @Query("SELECT * FROM notes WHERE is_deleted = 0")
    suspend fun getAllNotesSync(): List<NoteEntity>
}

@Dao
interface FeedbackDraftDao {
    @Query("SELECT * FROM feedback_drafts WHERE is_deleted = 0 ORDER BY updated_at DESC")
    fun observeDrafts(): Flow<List<FeedbackDraftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: FeedbackDraftEntity)
}
