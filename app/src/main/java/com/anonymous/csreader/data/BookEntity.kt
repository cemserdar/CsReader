package com.anonymous.csreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val uri: String,
    val type: String, // "epub" or "pdf"
    val progress: Float, // 0.0f to 1.0f
    val lastCfi: String?,
    val lastPage: Int?,
    val lastRead: Long,
    val addedDate: Long,
    val favorite: Boolean
)
