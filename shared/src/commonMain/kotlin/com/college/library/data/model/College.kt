package com.college.library.data.model

import kotlinx.serialization.Serializable

/**
 * Metadata for a college / institution participating in the NEXLIB
 * multi-college network.
 *
 * Firestore: /colleges/{collegeId}
 * Data sub-collections live under /institutions/{collegeId}/{books|ebooks|members|issued_books|reservations}.
 */
@Serializable
data class College(
    val collegeId: String = "",
    val collegeName: String = "",
    val inviteCode: String = "",
    val ownerUid: String = "",
    val directorUid: String = "",
    val createdAt: Long = 0L,
    val booksCount: Long = 0,
    val membersCount: Long = 0,
    val circulationCount: Long = 0,
    val lastSyncAt: Long = 0L
)
