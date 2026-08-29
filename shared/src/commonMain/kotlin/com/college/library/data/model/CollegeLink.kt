package com.college.library.data.model

import kotlinx.serialization.Serializable

/** The JSON payload embedded inside a college QR link. */
@Serializable
data class CollegeLink(
    val collegeId: String,
    val collegeName: String,
    val linkedAt: Long
)
