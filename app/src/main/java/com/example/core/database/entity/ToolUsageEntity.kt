package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "tool_usage")
data class ToolUsageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "tool_id")
    val toolId: String,
    @ColumnInfo(name = "usage_count")
    val usageCount: Int = 0,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "favorite_order")
    val favoriteOrder: Int = 0,
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "device_id")
    val deviceId: String = "",
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)
