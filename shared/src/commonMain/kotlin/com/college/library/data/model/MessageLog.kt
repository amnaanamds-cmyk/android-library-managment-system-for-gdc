package com.college.library.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageLog(
    val id: Long = 0,
    val memberId: Long,
    val memberName: String,
    val memberPhone: String = "",
    val bookTitle: String,
    val channel: String, // "SMS" or "WhatsApp"
    val message: String,
    val sentDate: String,
    val status: String = "Sent",
    val lastUpdated: Long = 0
)
