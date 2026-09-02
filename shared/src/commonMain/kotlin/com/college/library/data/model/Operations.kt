package com.college.library.data.model

import kotlinx.serialization.Serializable

/**
 * "Operations" records — the library-management features beyond the core
 * catalogue and circulation loop: the gate log, acquisitions, inter-library
 * loans, serials, book transfers and the purchase wishlist.
 *
 * All six live under the tenant root and carry the standard sync envelope
 * (syncId as the document id, collegeId, lastUpdated, deleted, syncStatus), so
 * they replicate between Android, Desktop and Web exactly like books and
 * members do.
 *
 * Field names are camelCase to match the web app and the rest of the sync
 * envelope. The desktop app's local SQLite columns are partly snake_case
 * (visitor_log.entry_time, book_transfers.from_college) and are mapped on the
 * way in and out — see gdc_desktop/services/operations_service.py.
 *
 * Before this schema existed these features were local-only on the desktop and
 * Firestore-backed on the web with no agreed field names, so the "shared"
 * library data was not actually shared.
 *
 * Keep in step with:
 *   gdc_desktop/services/operations_service.py
 *   web-app/src/lib/operations.ts
 */

/** Collection names under /institutions/{collegeId}/. */
object OperationsCollections {
    const val VISITOR_LOG = "visitor_log"
    const val PURCHASE_ORDERS = "purchase_orders"
    const val BOOK_TRANSFERS = "book_transfers"
    const val SERIALS = "serials"
    const val ILL_REQUESTS = "ill_requests"
    const val WISHLIST = "wishlist"
    const val READING_ROOM = "reading_room"
    const val LOST_FOUND = "lost_found"
    const val EVENTS = "library_events"
}

/** Gate log: who entered the library and when. */
@Serializable
data class VisitorEntry(
    val syncId: String = "",
    val name: String = "",
    /** "Student" | "Staff" | "Guest" */
    val visitorType: String = "Student",
    val purpose: String = "",
    /** Member id when the visitor is a registered patron, else blank. */
    val memberId: String = "",
    val entryTime: Long = 0L,
    /** Null until the visitor signs out. */
    val exitTime: Long? = null,
    /** "YYYY-MM-DD", so a day's log is one indexed query. */
    val dateStr: String = "",
    val collegeId: String = "",
    val lastUpdated: Long = 0L,
    val deleted: Boolean = false,
    val syncStatus: String = "synced",
) {
    val isInside: Boolean get() = exitTime == null
}

/** Acquisitions: a purchase order raised with a vendor. */
@Serializable
data class PurchaseOrder(
    val syncId: String = "",
    val vendorName: String = "",
    val bookTitle: String = "",
    val isbn: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val totalAmount: Double = 0.0,
    /** Pending | Approved | Ordered | Shipped | Received | Cancelled */
    val status: String = STATUS_PENDING,
    val orderDate: Long = 0L,
    val expectedDate: String = "",
    val notes: String = "",
    val collegeId: String = "",
    val lastUpdated: Long = 0L,
    val deleted: Boolean = false,
    val syncStatus: String = "synced",
) {
    companion object {
        const val STATUS_PENDING = "Pending"
        val STATUSES = listOf(
            "Pending", "Approved", "Ordered", "Shipped", "Received", "Cancelled",
        )
    }
}

/** A book moving between two colleges in the network. */
@Serializable
data class BookTransfer(
    val syncId: String = "",
    val fromCollege: String = "",
    val toCollege: String = "",
    val bookTitle: String = "",
    val bookIsbn: String = "",
    val quantity: Int = 1,
    /** requested | approved | dispatched | received | rejected */
    val status: String = STATUS_REQUESTED,
    val requestedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val notes: String = "",
    val collegeId: String = "",
    val lastUpdated: Long = 0L,
    val deleted: Boolean = false,
    val syncStatus: String = "synced",
) {
    companion object {
        const val STATUS_REQUESTED = "requested"
        val STATUSES = listOf("requested", "approved", "dispatched", "received", "rejected")
    }
}

/** A periodical the library subscribes to. */
@Serializable
data class Serial(
    val syncId: String = "",
    val title: String = "",
    val issn: String = "",
    /** Daily | Weekly | Fortnightly | Monthly | Quarterly | Annual */
    val frequency: String = "Monthly",
    val publisher: String = "",
    /** Active | Lapsed | Cancelled */
    val status: String = "Active",
    val subscriptionEnd: String = "",
    /** ISO date of the most recently received issue. */
    val lastIssueReceived: String = "",
    val issuesReceived: Int = 0,
    val collegeId: String = "",
    val lastUpdated: Long = 0L,
    val deleted: Boolean = false,
    val syncStatus: String = "synced",
) {
    companion object {
        val FREQUENCIES = listOf(
            "Daily", "Weekly", "Fortnightly", "Monthly", "Quarterly", "Annual",
        )
        val STATUSES = listOf("Active", "Lapsed", "Cancelled")
    }
}

/** Inter-library loan request raised against another institution. */
@Serializable
data class IllRequest(
    val syncId: String = "",
    val bookTitle: String = "",
    val author: String = "",
    val isbn: String = "",
    /** Member the request is on behalf of. */
    val memberId: String = "",
    val memberName: String = "",
    /** Institution id or name the request is aimed at. */
    val targetInstitution: String = "",
    /** Pending | Approved | Dispatched | Fulfilled | Returned | Rejected */
    val status: String = STATUS_PENDING,
    val requestDate: Long = 0L,
    val fulfilledDate: Long? = null,
    val notes: String = "",
    val collegeId: String = "",
    val lastUpdated: Long = 0L,
    val deleted: Boolean = false,
    val syncStatus: String = "synced",
) {
    companion object {
        const val STATUS_PENDING = "Pending"
        val STATUSES = listOf(
            "Pending", "Approved", "Dispatched", "Fulfilled", "Returned", "Rejected",
        )
    }
}

/** A title a member has asked the library to acquire. */
@Serializable
data class WishlistItem(
    val syncId: String = "",
    val title: String = "",
    val author: String = "",
    val isbn: String = "",
    val requestedByMemberId: String = "",
    val requestedByName: String = "",
    val reason: String = "",
    /** Requested | UnderReview | Approved | Ordered | Declined */
    val status: String = STATUS_REQUESTED,
    /** Number of members who asked for the same title. */
    val votes: Int = 1,
    val requestedAt: Long = 0L,
    val collegeId: String = "",
    val lastUpdated: Long = 0L,
    val deleted: Boolean = false,
    val syncStatus: String = "synced",
) {
    companion object {
        const val STATUS_REQUESTED = "Requested"
        val STATUSES = listOf("Requested", "UnderReview", "Approved", "Ordered", "Declined")
    }
}

/**
 * A reading-room seat and who currently occupies it.
 *
 * The desktop and web Enterprise panels both held seat state in memory only, so
 * an occupied seat vanished on refresh and no other device could see it. Seats
 * are records now, keyed by seat number within the college.
 */
@Serializable
data class ReadingRoomSeat(
    val syncId: String = "",
    val seatNumber: Int = 0,
    val occupantMemberId: String = "",
    val occupantName: String = "",
    val occupiedAt: Long? = null,
    val collegeId: String = "",
    val lastUpdated: Long = 0L,
    val deleted: Boolean = false,
    val syncStatus: String = "synced",
) {
    val isOccupied: Boolean get() = occupantName.isNotBlank()
}

/** An item lost or found on library premises. */
@Serializable
data class LostFoundItem(
    val syncId: String = "",
    val itemName: String = "",
    val description: String = "",
    val location: String = "",
    /** Lost | Found | Claimed | Disposed */
    val status: String = STATUS_FOUND,
    val reportedBy: String = "",
    val reportedAt: Long = 0L,
    val claimedBy: String = "",
    val collegeId: String = "",
    val lastUpdated: Long = 0L,
    val deleted: Boolean = false,
    val syncStatus: String = "synced",
) {
    companion object {
        const val STATUS_FOUND = "Found"
        val STATUSES = listOf("Lost", "Found", "Claimed", "Disposed")
    }
}

/** A library event: book fair, orientation, reading week. */
@Serializable
data class LibraryEvent(
    val syncId: String = "",
    val title: String = "",
    val description: String = "",
    /** ISO date "YYYY-MM-DD". */
    val eventDate: String = "",
    val venue: String = "",
    val organiser: String = "",
    /** Planned | Ongoing | Completed | Cancelled */
    val status: String = "Planned",
    val attendees: Int = 0,
    val collegeId: String = "",
    val lastUpdated: Long = 0L,
    val deleted: Boolean = false,
    val syncStatus: String = "synced",
) {
    companion object {
        val STATUSES = listOf("Planned", "Ongoing", "Completed", "Cancelled")
    }
}
