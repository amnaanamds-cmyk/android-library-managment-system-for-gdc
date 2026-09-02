package com.college.library.data

import com.college.library.data.model.LibrarySettings
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * Reads and writes the per-institution [LibrarySettings] document.
 *
 * Callers should treat the local cache as the source for calculations (so an
 * offline device still charges the right fine) and this repository as the way
 * that cache is kept current.
 */
class SettingsRepository {

    private fun docRef(collegeId: String) =
        Firebase.firestore
            .collection("institutions")
            .document(collegeId)
            .collection(LibrarySettings.COLLECTION)
            .document(LibrarySettings.DOCUMENT_ID)

    /** One-shot fetch. Returns null when unavailable, never throws. */
    suspend fun fetch(collegeId: String): LibrarySettings? {
        if (collegeId.isEmpty() || !FirebaseAvailability.isInitialized) return null
        return try {
            val snap = docRef(collegeId).get()
            if (snap.exists) snap.data<LibrarySettings>().sanitised() else null
        } catch (e: Exception) {
            null
        }
    }

    /** Live stream of settings changes, so a policy edit propagates instantly. */
    fun observe(collegeId: String): Flow<LibrarySettings> {
        if (collegeId.isEmpty() || !FirebaseAvailability.isInitialized) {
            return flowOf(LibrarySettings())
        }
        return docRef(collegeId).snapshots().map { snap ->
            if (snap.exists) {
                runCatching { snap.data<LibrarySettings>().sanitised() }
                    .getOrElse { LibrarySettings() }
            } else {
                LibrarySettings()
            }
        }
    }

    /**
     * Persist [settings] for [collegeId]. Returns true on success.
     *
     * Writing settings requires an admin-level role under the security rules,
     * so a librarian's save is expected to fail; the caller should keep the
     * local value and report that the change was not shared.
     */
    suspend fun save(
        collegeId: String,
        settings: LibrarySettings,
        updatedBy: String = "",
    ): Boolean {
        if (collegeId.isEmpty() || !FirebaseAvailability.isInitialized) return false
        return try {
            docRef(collegeId).set(
                settings.sanitised().copy(
                    lastUpdated = Clock.System.now().toEpochMilliseconds(),
                    updatedBy = updatedBy,
                    updatedByPlatform = "android",
                ),
                merge = true,
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
