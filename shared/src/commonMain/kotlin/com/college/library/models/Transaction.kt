package com.college.library.models

import kotlinx.serialization.Serializable

@Serializable
data class Transaction(
    val syncId: String,
    val bookId: String,
    val memberId: String,
    val checkoutDate: Long,
    val returnDate: Long?,
    val serverTimestamp: Long
)
