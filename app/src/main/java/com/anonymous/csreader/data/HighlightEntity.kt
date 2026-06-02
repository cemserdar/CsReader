package com.anonymous.csreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val cfiRange: String?,
    val page: Int?,
    val text: String,
    val note: String?,
    val color: String, // "yellow" | "green" | "pink" | "blue" | "underline"
    val date: Long
)
