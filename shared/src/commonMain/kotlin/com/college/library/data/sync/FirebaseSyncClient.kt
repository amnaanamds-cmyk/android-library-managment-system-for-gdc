package com.college.library.data.sync

import com.college.library.data.FirebaseAvailability
import com.college.library.data.model.UserProfile
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

data class AuthToken(
    val token: String,
    val institutionId: String,
    val role: String,
    val librarianName: String
)

interface FirebaseSyncClient {
    suspend fun authenticate(email: String, password: String): Result<AuthToken>
    suspend fun registerCollege(
        collegeName: String,
        collegeId: String,
        email: String,
        password: String,
    ): Result<AuthToken>
    suspend fun joinCollege(collegeId: String, email: String, password: String): Result<AuthToken>
}

/**
 * Real Firebase Authentication client backed by Firestore user profiles.
 *
 * The offline demo path is reachable ONLY when no Firebase project is
 * configured at all. It used to be wired as a `recoverCatching` on every
 * failure, which meant a wrong password, a revoked account, or a missing user
 * profile all fell through to the demo credentials and signed the device in
 * against the hardcoded institution "gdc-peshawar". The user saw a successful
 * login and then an app that synced nothing, because it was attached to an
 * institution that holds none of their data. Real failures now surface as
 * failures.
 */
class FirebaseSyncClientImpl : FirebaseSyncClient {

    /** True when there is no Firebase project to talk to at all. */
    private fun firebaseUnavailable(): Boolean = !FirebaseAvailability.isInitialized

    override suspend fun authenticate(email: String, password: String): Result<AuthToken> {
        if (firebaseUnavailable()) return runCatching { offlineAuthenticate(email, password) }
        return runCatching { realAuthenticate(email, password) }
    }

    override suspend fun registerCollege(
        collegeName: String,
        collegeId: String,
        email: String,
        password: String,
    ): Result<AuthToken> {
        if (firebaseUnavailable()) return runCatching { offlineAuthenticate(email, password) }
        return runCatching {
            val cid = collegeId.trim().uppercase()
            require(cid.isNotEmpty()) { "A college ID is required to register an institution." }

            val result = Firebase.auth.createUserWithEmailAndPassword(email, password)
            val uid = result.user?.uid ?: throw Exception("Sign up failed")

            // Create the institution the user actually named. This previously
            // hardcoded "gdc11", so every college registered from Android was
            // silently pointed at one shared tenant and saw another college's
            // catalogue. ownerUid is what the Firestore rules use to recognise
            // this account as the institution's owner.
            Firebase.firestore.collection("institutions").document(cid).set(
                mapOf(
                    "name" to collegeName,
                    "inviteCode" to cid,
                    "ownerUid" to uid,
                    "createdAt" to Clock.System.now().toEpochMilliseconds(),
                ),
                merge = true,
            )

            // Seed the global user profile so future logins resolve the role.
            Firebase.firestore.collection("users").document(uid).set(
                UserProfile(uid = uid, email = email, name = collegeName, institutionId = cid, role = "Director")
            )

            AuthToken(token = uid, institutionId = cid, role = "Director", librarianName = collegeName)
        }
    }

    override suspend fun joinCollege(collegeId: String, email: String, password: String): Result<AuthToken> {
        if (firebaseUnavailable()) return runCatching { offlineAuthenticate(email, password) }
        return runCatching {
            val cid = collegeId.trim().uppercase()
            val result = Firebase.auth.signInWithEmailAndPassword(email, password)
            val uid = result.user?.uid ?: throw Exception("Sign in failed")

            // Verify the institution exists before attaching to it. Joining a
            // non-existent ID used to succeed and leave the device pointed at an
            // empty tenant that never syncs — indistinguishable, to the user,
            // from sync being broken.
            val institution = Firebase.firestore.collection("institutions").document(cid).get()
            if (!institution.exists) {
                throw Exception("No institution found with ID \"$cid\". Check the code and try again.")
            }

            // set(merge) rather than update(): update() fails when the user has
            // no profile document yet, which is the normal state for an account
            // created outside this app.
            Firebase.firestore.collection("users").document(uid).set(
                mapOf("uid" to uid, "email" to email, "institutionId" to cid, "role" to "Librarian"),
                merge = true,
            )
            AuthToken(token = uid, institutionId = cid, role = "Librarian", librarianName = email)
        }
    }

    private suspend fun realAuthenticate(email: String, password: String): AuthToken {
        val result = Firebase.auth.signInWithEmailAndPassword(email, password)
        val uid = result.user?.uid ?: throw Exception("No user returned")
        val profile = Firebase.firestore.collection("users").document(uid).get().data<UserProfile>()
        return AuthToken(
            token = profile.uid.ifEmpty { uid },
            institutionId = profile.institutionId,
            role = profile.role,
            librarianName = profile.name.ifEmpty { email }
        )
    }

    // Demo/offline fallback, used ONLY when no Firebase project is configured.
    // Never used to paper over a real authentication failure.
    private suspend fun offlineAuthenticate(email: String, password: String): AuthToken {
        delay(300) // Simulate network
        return when {
            email.contains("director") && password == "director" -> {
                AuthToken("director-token", "gdc-peshawar", "Director", "Director General")
            }
            email.isNotBlank() && password == "admin" -> {
                AuthToken("mock-token-xyz", "gdc-peshawar", "Librarian", "Admin User")
            }
            else -> {
                throw Exception("Invalid credentials. (Hint: password is 'admin' or 'director')")
            }
        }
    }
}
