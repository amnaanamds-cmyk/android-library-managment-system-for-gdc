package com.college.library.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BookReview(
    val id: Long = 0,
    val syncId: String = "",
    val bookId: Long,
    val memberId: Long,
    val memberName: String,
    val rating: Int,
    val reviewText: String,
    val reviewDate: String,
    val lastUpdated: Long = 0,
    val deleted: Boolean = false
)
