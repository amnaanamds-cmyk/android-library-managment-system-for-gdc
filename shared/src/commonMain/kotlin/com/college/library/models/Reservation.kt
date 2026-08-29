package com.college.library.models

import kotlinx.serialization.Serializable

@Serializable
data class Reservation(
    val syncId: String,
    val memberId: String,
    val bookId: String,
    val requestDate: Long,
    val status: String,
    val position: Int,
    val serverTimestamp: Long
)
