package com.example.core.database

import androidx.room.TypeConverter
import com.example.core.database.entity.NoteType

class Converters {
    @TypeConverter
    fun fromNoteType(value: NoteType): String = value.name

    @TypeConverter
    fun toNoteType(value: String): NoteType =
        runCatching { NoteType.valueOf(value) }.getOrDefault(NoteType.TEXT)
}
