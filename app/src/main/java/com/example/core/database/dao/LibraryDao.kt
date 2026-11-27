package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.database.entity.CategoryEntity
import com.example.core.database.entity.CollectionEntity
import com.example.core.database.entity.DocumentCollectionCrossRef
import com.example.core.database.entity.LibraryDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query("SELECT * FROM categories WHERE is_deleted = 0 ORDER BY sort_order ASC, name ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :categoryId AND is_deleted = 0 LIMIT 1")
    suspend fun getCategoryById(categoryId: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultCategories(categories: List<CategoryEntity>)

    @Query(
        """
        SELECT d.* FROM library_documents d
        LEFT JOIN categories c ON d.category_id = c.id
        WHERE d.is_deleted = 0
          AND (c.is_locked IS NULL OR c.is_locked = 0 OR :includeLocked = 1)
          AND (d.is_locked = 0 OR :includeLocked = 1)
        ORDER BY d.last_opened_at DESC
        """
    )
    fun observeDocuments(includeLocked: Boolean = false): Flow<List<LibraryDocumentEntity>>

    @Query(
        """
        SELECT d.* FROM library_documents d
        LEFT JOIN categories c ON d.category_id = c.id
        WHERE d.is_deleted = 0
          AND d.category_id = :categoryId
          AND (c.is_locked IS NULL OR c.is_locked = 0 OR :includeLocked = 1)
          AND (d.is_locked = 0 OR :includeLocked = 1)
        ORDER BY d.last_opened_at DESC
        """
    )
    fun observeDocumentsByCategory(
        categoryId: String,
        includeLocked: Boolean = false
    ): Flow<List<LibraryDocumentEntity>>

    @Query(
        """
        SELECT d.* FROM library_documents d
        LEFT JOIN categories c ON d.category_id = c.id
        WHERE d.is_deleted = 0
          AND (c.is_locked IS NULL OR c.is_locked = 0)
          AND d.is_locked = 0
        ORDER BY d.last_opened_at DESC
        LIMIT :limit
        """
    )
    fun observeRecentDocuments(limit: Int = 10): Flow<List<LibraryDocumentEntity>>

    @Query(
        """
        SELECT d.* FROM library_documents d
        JOIN library_documents_fts fts ON d.rowid = fts.rowid
        LEFT JOIN categories c ON d.category_id = c.id
        WHERE library_documents_fts MATCH :ftsQuery
          AND d.is_deleted = 0
          AND (c.is_locked IS NULL OR c.is_locked = 0 OR :includeLocked = 1)
          AND (d.is_locked = 0 OR :includeLocked = 1)
        ORDER BY d.last_opened_at DESC
        """
    )
    fun searchDocumentsFts(
        ftsQuery: String,
        includeLocked: Boolean = false
    ): Flow<List<LibraryDocumentEntity>>

    @Query("SELECT * FROM library_documents WHERE id = :documentId AND is_deleted = 0 LIMIT 1")
    suspend fun getDocumentById(documentId: String): LibraryDocumentEntity?

    @Query("SELECT * FROM library_documents WHERE id = :documentId AND is_deleted = 0 LIMIT 1")
    fun observeDocumentById(documentId: String): Flow<LibraryDocumentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: LibraryDocumentEntity)

    @Update
    suspend fun updateDocument(document: LibraryDocumentEntity)

    @Query(
        """
        UPDATE library_documents
        SET is_deleted = 1, updated_at = :updatedAt, device_id = :deviceId
        WHERE id = :documentId
        """
    )
    suspend fun softDeleteDocument(documentId: String, updatedAt: Long, deviceId: String)

    @Query(
        """
        UPDATE library_documents
        SET is_deleted = 0, updated_at = :updatedAt, device_id = :deviceId
        WHERE id = :documentId
        """
    )
    suspend fun restoreDocument(documentId: String, updatedAt: Long, deviceId: String)

    @Query(
        """
        UPDATE library_documents
        SET category_id = :categoryId, is_locked = :isLocked, updated_at = :updatedAt, device_id = :deviceId
        WHERE id = :documentId
        """
    )
    suspend fun moveDocumentToCategory(
        documentId: String,
        categoryId: String?,
        isLocked: Boolean,
        updatedAt: Long,
        deviceId: String
    )

    @Query(
        """
        UPDATE library_documents
        SET last_opened_at = :timestamp, updated_at = :timestamp, device_id = :deviceId
        WHERE id = :documentId
        """
    )
    suspend fun markDocumentOpened(documentId: String, timestamp: Long, deviceId: String)

    @Query(
        """
        SELECT * FROM library_documents
        WHERE is_deleted = 0 AND is_locked = 1
        ORDER BY last_opened_at DESC
        """
    )
    fun observeLockedDocuments(): Flow<List<LibraryDocumentEntity>>

    @Query(
        """
        UPDATE library_documents
        SET title = :title, file_name = :fileName, updated_at = :updatedAt, device_id = :deviceId
        WHERE id = :documentId
        """
    )
    suspend fun updateDocumentTitle(
        documentId: String,
        title: String,
        fileName: String,
        updatedAt: Long,
        deviceId: String
    )

    @Query(
        """
        UPDATE library_documents
        SET is_locked = :isLocked, updated_at = :updatedAt, device_id = :deviceId
        WHERE id = :documentId
        """
    )
    suspend fun setDocumentLocked(documentId: String, isLocked: Boolean, updatedAt: Long, deviceId: String)

    @Query("SELECT * FROM categories WHERE is_deleted = 0")
    suspend fun getAllCategoriesSync(): List<CategoryEntity>

    @Query("SELECT * FROM library_documents WHERE is_deleted = 0")
    suspend fun getAllDocumentsSync(): List<LibraryDocumentEntity>

    @Query("SELECT * FROM collections WHERE is_deleted = 0 ORDER BY name ASC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollection(collection: CollectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDocumentToCollection(crossRef: DocumentCollectionCrossRef)
}
