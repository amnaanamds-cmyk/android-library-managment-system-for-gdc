package com.college.library.data.model

import kotlinx.serialization.Serializable

/**
 * Global user profile. Written during onboarding/registration and read on
 * every login to resolve the user's institution(s) and role.
 *
 * Firestore: /users/{uid}
 */
@Serializable
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val institutionId: String = "",
    val role: String = "Librarian",
    val collegeIds: List<String> = emptyList()
)
