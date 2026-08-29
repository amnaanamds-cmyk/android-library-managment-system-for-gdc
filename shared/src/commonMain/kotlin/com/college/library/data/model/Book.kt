package com.college.library.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Book(
    val syncId: String = "",
    val id: Long = 0,
    val isbn: String = "",
    val accNo: String = "",
    val title: String = "Unknown Title",
    val author: String = "Unknown Author",
    val publisher: String = "",
    val publisherPlace: String = "",
    val publishDate: String = "",
    val edition: String = "",
    val pages: Int = 0,
    val procurement: String = "",
    val volume: String = "",
    val price: Double = 0.0,
    val status: String = "Available",
    val isDigital: Boolean = false,
    val digitalUrl: String? = null,
    val category: String = "Uncategorized",
    val marcData: String? = null,
    val callNumber: String = "",      // DDC/LC call number for spine labels
    val authorCutter: String = "",    // Author cutter code
    val lastUpdated: Long = 0L,
    val deleted: Boolean = false,
    val collegeId: String = "",
    val syncStatus: String = "synced"
)
