package com.college.library.data.model

import kotlinx.serialization.Serializable

/**
 * A college director account. Directors oversee one or more colleges and
 * are the only role allowed to view the multi-college dashboard.
 *
 * Firestore: /directors/{uid}
 */
@Serializable
data class Director(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val collegeIds: List<String> = emptyList(),
    val assignedAt: Long = 0L
)
