package com.college.library.models

import kotlinx.serialization.Serializable

@Serializable
data class Member(
    val syncId: String,
    val name: String,
    val rollNumber: String,
    val email: String,
    val status: String,
    val serverTimestamp: Long,
    val softDelete: Int = 0
)
