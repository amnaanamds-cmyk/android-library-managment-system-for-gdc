package com.college.library.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val isbn: String,
    val accNo: String,
    val title: String,
    val author: String,
    val publisher: String,
    val publisherPlace: String,
    val publishDate: String,
    val edition: String,
    val pages: Int,
    val procurement: String,
    val volume: String,
    val price: Double,
    val status: String = "Available",
    val isDigital: Boolean = false,
    val digitalUrl: String? = null,
    val category: String = "Uncategorized"
)
