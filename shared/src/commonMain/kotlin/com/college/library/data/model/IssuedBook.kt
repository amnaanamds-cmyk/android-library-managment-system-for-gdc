package com.college.library.data.model

import kotlinx.serialization.Serializable

@Serializable
data class IssuedBook(
    val syncId: String = "",
    val id: Long = 0,
    val bookId: Long = 0L,
    val bookTitle: String = "",
    val bookIsbn: String = "",
    val memberId: Long = 0L,
    val memberName: String = "",
    val memberMemberId: String = "",
    val issueDate: String = "",
    val dueDate: String = "",
    val returnDate: String? = null,
    val fine: Double = 0.0,
    val status: String = "Issued",
    val lastUpdated: Long = 0L,
    val deleted: Boolean = false,
    val collegeId: String = "",
    val syncStatus: String = "synced"
)

