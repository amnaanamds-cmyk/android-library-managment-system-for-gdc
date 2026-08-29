package com.college.library.models

import kotlinx.serialization.Serializable

@Serializable
data class Book(
    val syncId: String,
    val title: String,
    val author: String,
    val isbn: String,
    val available: Int,
    val serverTimestamp: Long,
    val softDelete: Int = 0
)
