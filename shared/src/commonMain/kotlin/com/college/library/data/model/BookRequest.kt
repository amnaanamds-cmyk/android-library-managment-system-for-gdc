package com.college.library.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BookRequest(
    val id: Long = 0,
    val syncId: String = "",
    val title: String,
    val author: String,
    val memberId: Long,
    val memberName: String,
    val requestDate: String,
    val status: String = "Pending",
    val lastUpdated: Long = 0,
    val deleted: Boolean = false
)
