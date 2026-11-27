package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    @ColumnInfo(name = "icon_name")
    val iconName: String,
    @ColumnInfo(name = "color_hex")
    val colorHex: Long,
    @ColumnInfo(name = "is_locked")
    val isLocked: Boolean = false,
    @ColumnInfo(name = "is_system_default")
    val isSystemDefault: Boolean = false,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "device_id")
    val deviceId: String = "",
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)
