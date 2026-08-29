package com.college.library.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Reservation(
    val syncId: String = "",
    val id: Long = 0,
    val bookId: Long = 0L,
    val bookTitle: String = "",
    val memberId: Long = 0L,
    val memberName: String = "",
    val reservedDate: String = "",
    val status: String = "Pending",
    val notifiedDate: String? = null,
    val lastUpdated: Long = 0L,
    val deleted: Boolean = false,
    val collegeId: String = "",
    val syncStatus: String = "synced"
)

