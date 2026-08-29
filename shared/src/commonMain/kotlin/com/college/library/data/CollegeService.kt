package com.college.library.data

import com.college.library.data.model.*
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * Firestore service for the multi-college director network.
 *
 * Collections used:
 *   /colleges/{collegeId}                     - college metadata (name, inviteCode, directorUid, counters)
 *   /institutions/{collegeId}/...             - existing tenant data (books, ebooks, members, ...)
 *   /directors/{uid}                          - director account + assigned college ids
 *   /users/{uid}                              - global user profile (role/institution)
 *   /directorRequests/{id}                    - invite-code based assignment requests
 */
class CollegeService {

    private val db by lazy { Firebase.firestore }

    // ─────────────────────────────────────────────────────────────
    // College lifecycle
    // ─────────────────────────────────────────────────────────────

    /** Creates a new college with a user-provided College Unique ID and seeds the tenant root. */
    suspend fun createCollege(collegeId: String, collegeName: String, ownerUid: String): Result<College> {
        if (!FirebaseAvailability.isInitialized) {
            return Result.failure(Exception("Firebase is not configured. Cannot create a college in offline mode."))
        }
        return runCatching {
            val college = College(
                collegeId = collegeId.trim().uppercase(),
                collegeName = collegeName,
                inviteCode = collegeId.trim().uppercase(), // Maintained for backwards compatibility
                ownerUid = ownerUid,
                directorUid = ownerUid,
                createdAt = nowMillis()
            )
            db.collection("colleges").document(college.collegeId).set(college)
            db.collection("institutions").document(college.collegeId).set(
                mapOf(
                    "name" to collegeName,
                    "inviteCode" to college.inviteCode,
                    "createdAt" to college.createdAt,
                    "directorUid" to college.directorUid
                )
            )
            college
        }
    }

    /** Looks up a college by its College Unique ID (used by "join existing"). */
    suspend fun findCollegeById(collegeId: String): Result<College> {
        if (!FirebaseAvailability.isInitialized) {
            return Result.failure(Exception("Firebase is not configured. Cannot join a college in offline mode."))
        }
        return runCatching {
            val doc = db.collection("colleges").document(collegeId.trim().uppercase()).get()
            if (doc.exists) {
                doc.data<College>()
            } else {
                throw Exception("No college found for ID $collegeId")
            }
        }
    }

    /** Assigns a director to a college and records the assignment on the director document. */
    suspend fun assignDirector(collegeId: String, directorUid: String) {
        if (!FirebaseAvailability.isInitialized) return
        db.collection("colleges").document(collegeId).update("directorUid" to directorUid)
        
        // Ensure the director document exists
        val directorDoc = db.collection("directors").document(directorUid)
        if (directorDoc.get().exists) {
            directorDoc.update(
                "collegeIds" to FieldValue.arrayUnion(collegeId),
                "assignedAt" to nowMillis()
            )
        } else {
            db.collection("directors").document(directorUid).set(
                Director(uid = directorUid, collegeIds = listOf(collegeId), assignedAt = nowMillis())
            )
        }
        
        // Refresh stats for this college immediately
        refreshCollegeStats(collegeId)
    }

    /** 
     * Aggregates live data from a college's sub-collections and updates the master registry.
     * This ensures the Director Dashboard sees real, live numbers.
     */
    suspend fun refreshCollegeStats(collegeId: String) {
        if (!FirebaseAvailability.isInitialized) return
        runCatching {
            val books = db.collection("institutions").document(collegeId).collection("books").get().documents
            val members = db.collection("institutions").document(collegeId).collection("members").get().documents
            val issues = db.collection("institutions").document(collegeId).collection("issued_books").get().documents
            
            val booksCount = books.count { !it.data<Book>().deleted }.toLong()
            val membersCount = members.count { !it.data<Member>().deleted }.toLong()
            val circulationCount = issues.count { !it.data<IssuedBook>().deleted && it.data<IssuedBook>().status == "Issued" }.toLong()
            
            db.collection("colleges").document(collegeId).update(
                "booksCount" to booksCount,
                "membersCount" to membersCount,
                "circulationCount" to circulationCount,
                "lastSyncAt" to nowMillis()
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Director account
    // ─────────────────────────────────────────────────────────────

    suspend fun saveDirector(director: Director) {
        if (!FirebaseAvailability.isInitialized) return
        db.collection("directors").document(director.uid).set(director, merge = true)
    }

    suspend fun getDirector(uid: String): Director? {
        if (!FirebaseAvailability.isInitialized) return null
        val doc = db.collection("directors").document(uid).get()
        return if (doc.exists) doc.data<Director>() else null
    }

    // ─────────────────────────────────────────────────────────────
    // Real-time streams for the dashboard
    // ─────────────────────────────────────────────────────────────

    /** Colleges assigned to the given director (live). */
    fun observeColleges(directorUid: String): Flow<List<College>> {
        if (!FirebaseAvailability.isInitialized) return emptyFlow()
        return db.collection("colleges").where { "directorUid" equalTo directorUid }.snapshots().map { snap ->
            snap.documents.mapNotNull { doc -> runCatching { doc.data<College>() }.getOrNull() }
        }
    }

    /** Live college metadata (counters refresh as the college syncs). */
    fun observeCollege(collegeId: String): Flow<College?> {
        if (!FirebaseAvailability.isInitialized) return emptyFlow()
        return db.collection("colleges").document(collegeId).snapshots().map { doc ->
            if (doc.exists) runCatching { doc.data<College>() }.getOrNull() else null
        }
    }

    /** Live book catalog of a college (drill-down view). */
    fun observeCollegeBooks(collegeId: String): Flow<List<Book>> {
        if (!FirebaseAvailability.isInitialized) return emptyFlow()
        return db.collection("institutions").document(collegeId).collection("books").snapshots().map { snap ->
            snap.documents.mapNotNull { doc -> runCatching { doc.data<Book>() }.getOrNull() }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Union Catalog Support
    // ─────────────────────────────────────────────────────────────

    suspend fun getAllColleges(): List<College> {
        if (!FirebaseAvailability.isInitialized) return emptyList()
        return runCatching {
            db.collection("colleges").get().documents.mapNotNull { it.data<College>() }
        }.getOrDefault(emptyList())
    }

    suspend fun searchBooksInCollege(collegeId: String, query: String): List<Book> {
        if (!FirebaseAvailability.isInitialized) return emptyList()
        return runCatching {
            val docs = db.collection("institutions").document(collegeId).collection("books").get().documents
            docs.mapNotNull { it.data<Book>() }
                .filter { !it.deleted && (it.title.contains(query, true) || it.author.contains(query, true) || it.isbn.contains(query, true)) }
        }.getOrDefault(emptyList())
    }

    suspend fun submitIllRequest(targetCollegeId: String, bookTitle: String, requesterName: String, fromInstitution: String) {
        if (!FirebaseAvailability.isInitialized) return
        runCatching {
            val docRef = db.collection("institutions").document(targetCollegeId).collection("ill_requests").document
            val illData = mapOf(
                "id" to docRef.id,
                "bookTitle" to bookTitle,
                "requesterName" to requesterName,
                "fromInstitution" to fromInstitution,
                "status" to "Pending",
                "requestDate" to nowMillis()
            )
            docRef.set(illData)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
