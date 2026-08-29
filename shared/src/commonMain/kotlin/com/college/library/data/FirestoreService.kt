package com.college.library.data

import com.college.library.data.model.*
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/**
 * Service for interacting with Firestore.
 *
 * Schema (multi-college / multi-tenant):
 *   /institutions/{collegeId}/books/{syncId}
 *   /institutions/{collegeId}/ebooks/{syncId}      <- digital books, synced separately
 *   /institutions/{collegeId}/members/{syncId}
 *   /institutions/{collegeId}/issued_books/{syncId}
 *   /institutions/{collegeId}/reservations/{syncId}
 *
 * Every document carries an envelope of sync metadata:
 *   collegeId   - owning college (used for tenant isolation + LWW)
 *   lastModified- authoritative server timestamp (FieldValue.serverTimestamp)
 *   syncStatus  - "synced" | "pending"
 *
 * The Firestore instance is created lazily and every operation degrades
 * gracefully when no Firebase project is configured (offline demo mode).
 */
class FirestoreService {

    private val db by lazy { Firebase.firestore }

    private fun subcollection(collegeId: String, name: String): CollectionReference {
        return db.collection("institutions").document(collegeId).collection(name)
    }

    suspend fun clearAllCloudData(collegeId: String) {
        if (!FirebaseAvailability.isInitialized) return
        val collections = listOf("books", "ebooks", "members", "issued_books", "reservations")
        for (col in collections) {
            try {
                val docs = subcollection(collegeId, col).get().documents
                for (doc in docs) {
                    try {
                        doc.reference.delete()
                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {}
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Uploads (push). Digital books go to the dedicated `ebooks`
    // collection so they never block the main catalog sync.
    // ─────────────────────────────────────────────────────────────

    suspend fun uploadBook(collegeId: String, book: Book) {
        if (!FirebaseAvailability.isInitialized) return
        val doc = subcollection(collegeId, "books").document(book.syncId)
        val ts = if (book.lastUpdated > 0L) book.lastUpdated else kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val updatedBook = book.copy(lastUpdated = ts, collegeId = collegeId, syncStatus = "synced")
        doc.set(updatedBook, merge = true)
    }

    suspend fun uploadEbook(collegeId: String, ebook: Book) {
        if (!FirebaseAvailability.isInitialized) return
        val doc = subcollection(collegeId, "ebooks").document(ebook.syncId)
        val ts = if (ebook.lastUpdated > 0L) ebook.lastUpdated else kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val updatedEbook = ebook.copy(lastUpdated = ts, collegeId = collegeId, syncStatus = "synced")
        doc.set(updatedEbook, merge = true)
    }

    suspend fun uploadMember(collegeId: String, member: Member) {
        if (!FirebaseAvailability.isInitialized) return
        val doc = subcollection(collegeId, "members").document(member.syncId)
        val ts = if (member.lastUpdated > 0L) member.lastUpdated else kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val updatedMember = member.copy(lastUpdated = ts, collegeId = collegeId, syncStatus = "synced")
        doc.set(updatedMember, merge = true)
    }

    suspend fun uploadIssue(collegeId: String, issue: IssuedBook) {
        if (!FirebaseAvailability.isInitialized) return
        val doc = subcollection(collegeId, "issued_books").document(issue.syncId)
        val ts = if (issue.lastUpdated > 0L) issue.lastUpdated else kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val updatedIssue = issue.copy(lastUpdated = ts, collegeId = collegeId, syncStatus = "synced")
        doc.set(updatedIssue, merge = true)
    }

    suspend fun uploadReservation(collegeId: String, reservation: Reservation) {
        if (!FirebaseAvailability.isInitialized) return
        val doc = subcollection(collegeId, "reservations").document(reservation.syncId)
        val ts = if (reservation.lastUpdated > 0L) reservation.lastUpdated else kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val updatedReservation = reservation.copy(lastUpdated = ts, collegeId = collegeId, syncStatus = "synced")
        doc.set(updatedReservation, merge = true)
    }

    // ─────────────────────────────────────────────────────────────
    // Real-time listeners (snapshot streams). These are the heart of
    // the real-time sync engine - every remote change is emitted here.
    // ─────────────────────────────────────────────────────────────

    fun observeBooks(collegeId: String): Flow<List<Book>> {
        if (!FirebaseAvailability.isInitialized) return emptyFlow()
        return subcollection(collegeId, "books").snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { document -> runCatching { document.data<Book>() }.getOrNull() }
        }
    }

    fun observeEbooks(collegeId: String): Flow<List<Book>> {
        if (!FirebaseAvailability.isInitialized) return emptyFlow()
        return subcollection(collegeId, "ebooks").snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { document -> runCatching { document.data<Book>() }.getOrNull() }
        }
    }

    fun observeMembers(collegeId: String): Flow<List<Member>> {
        if (!FirebaseAvailability.isInitialized) return emptyFlow()
        return subcollection(collegeId, "members").snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { document -> runCatching { document.data<Member>() }.getOrNull() }
        }
    }

    fun observeIssues(collegeId: String): Flow<List<IssuedBook>> {
        if (!FirebaseAvailability.isInitialized) return emptyFlow()
        return subcollection(collegeId, "issued_books").snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { document -> runCatching { document.data<IssuedBook>() }.getOrNull() }
        }
    }

    fun observeReservations(collegeId: String): Flow<List<Reservation>> {
        if (!FirebaseAvailability.isInitialized) return emptyFlow()
        return subcollection(collegeId, "reservations").snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { document -> runCatching { document.data<Reservation>() }.getOrNull() }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Incremental pulls (used by the periodic full sync and the
    // initial backfill). Only records newer than `since` are fetched.
    // ─────────────────────────────────────────────────────────────

    private fun incrementalQuery(collegeId: String, sub: String, since: Long): Query {
        val base = subcollection(collegeId, sub)
        return if (since > 0L) base.where { "lastUpdated" greaterThan since } else base
    }

    suspend fun fetchBooks(collegeId: String, since: Long): List<Book> {
        if (!FirebaseAvailability.isInitialized) return emptyList()
        return incrementalQuery(collegeId, "books", since).get().documents
            .mapNotNull { document -> runCatching { document.data<Book>() }.getOrNull() }
    }

    suspend fun fetchEbooks(collegeId: String, since: Long): List<Book> {
        if (!FirebaseAvailability.isInitialized) return emptyList()
        return incrementalQuery(collegeId, "ebooks", since).get().documents
            .mapNotNull { document -> runCatching { document.data<Book>() }.getOrNull() }
    }

    suspend fun fetchMembers(collegeId: String, since: Long): List<Member> {
        if (!FirebaseAvailability.isInitialized) return emptyList()
        return incrementalQuery(collegeId, "members", since).get().documents
            .mapNotNull { document -> runCatching { document.data<Member>() }.getOrNull() }
    }

    suspend fun fetchIssues(collegeId: String, since: Long): List<IssuedBook> {
        if (!FirebaseAvailability.isInitialized) return emptyList()
        return incrementalQuery(collegeId, "issued_books", since).get().documents
            .mapNotNull { document -> runCatching { document.data<IssuedBook>() }.getOrNull() }
    }

    suspend fun fetchReservations(collegeId: String, since: Long): List<Reservation> {
        if (!FirebaseAvailability.isInitialized) return emptyList()
        return incrementalQuery(collegeId, "reservations", since).get().documents
            .mapNotNull { document -> runCatching { document.data<Reservation>() }.getOrNull() }
    }
}
