package com.college.library.data

import com.college.library.data.model.Book
import com.college.library.data.model.IssuedBook
import com.college.library.data.model.Member
import com.college.library.data.model.Reservation
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Publishes this college's aggregate snapshot to `/directorate_index/{collegeId}`,
 * the single registry the directorate portal reads.
 *
 * Why the clients publish this rather than a server: tenant data lives under
 * `/institutions/{collegeId}/...` and is readable only by that college's own
 * staff, Firestore has no cross-tenant aggregate query, and Cloud Functions are
 * not available on the Firebase Spark plan. So each college's own app computes
 * its rollup and writes one small document that directorate staff may read.
 *
 * The document holds counts and a heartbeat only — no book records, no patron
 * records — which is what makes it safe to expose network-wide.
 *
 * Keep the field names in step with:
 *   web-app/src/lib/directorate.ts
 *   gdc_desktop/services/registry_service.py
 */
object DirectorateRegistry {

    /** Bump when the document shape changes so readers can tell publishers apart. */
    const val SCHEMA_VERSION = 2

    private const val COLLECTION = "directorate_index"

    /** Republish at most this often, so a busy sync loop cannot spam writes. */
    private const val MIN_PUBLISH_INTERVAL_MS = 10 * 60 * 1000L

    private var lastPublishedAt = 0L

    @Serializable
    data class Snapshot(
        val institutionId: String = "",
        val name: String = "",
        val location: String = "",
        val booksCount: Int = 0,
        val ebooksCount: Int = 0,
        val membersCount: Int = 0,
        val activeLoans: Int = 0,
        val overdueCount: Int = 0,
        val reservationsCount: Int = 0,
        val finesOutstanding: Double = 0.0,
        val lastSyncAt: Long = 0L,
        val lastSyncPlatform: String = "android",
        val schemaVersion: Int = SCHEMA_VERSION,
    )

    /**
     * Publish [snapshot] for [collegeId].
     *
     * Never throws: a college that cannot publish its rollup must still be able
     * to run its library. Returns true when the write succeeded.
     */
    suspend fun publish(
        collegeId: String,
        snapshot: Snapshot,
        force: Boolean = false,
    ): Boolean {
        if (collegeId.isEmpty()) return false
        if (!FirebaseAvailability.isInitialized) return false

        val now = Clock.System.now().toEpochMilliseconds()
        if (!force && now - lastPublishedAt < MIN_PUBLISH_INTERVAL_MS) return false
        lastPublishedAt = now

        return try {
            Firebase.firestore.collection(COLLECTION).document(collegeId).set(
                snapshot.copy(
                    institutionId = collegeId,
                    lastSyncAt = now,
                    lastSyncPlatform = "android",
                    schemaVersion = SCHEMA_VERSION,
                ),
                merge = true,
            )
            true
        } catch (e: Exception) {
            // Expected when the signed-in user is not staff of this college.
            false
        }
    }

    /**
     * Build a snapshot from the records the sync engine has just received.
     *
     * Counting from the Firestore snapshots rather than the local cache keeps
     * this free of extra reads and means the published figures match exactly
     * what the server holds at the moment of publication.
     */
    fun buildSnapshot(
        collegeId: String,
        name: String,
        books: List<Book>,
        ebooks: List<Book>,
        members: List<Member>,
        issues: List<IssuedBook>,
        reservations: List<Reservation>,
        today: String,
    ): Snapshot {
        // An open loan is status == "Issued", matching the Python and TypeScript
        // predicates so all three platforms report the same figure.
        val active = issues.filter { !it.deleted && it.status.equals("Issued", ignoreCase = true) }
        val overdue = active.filter { it.dueDate.isNotEmpty() && it.dueDate < today }

        return Snapshot(
            institutionId = collegeId,
            name = name,
            booksCount = books.count { !it.deleted && !it.isDigital },
            ebooksCount = ebooks.count { !it.deleted } + books.count { !it.deleted && it.isDigital },
            membersCount = members.count { !it.deleted },
            activeLoans = active.size,
            overdueCount = overdue.size,
            reservationsCount = reservations.count {
                !it.deleted && it.status.equals("Pending", ignoreCase = true)
            },
            finesOutstanding = active.sumOf { it.fine },
        )
    }
}
