package com.college.library.data

import com.college.library.data.model.*

/**
 * Last-write-wins (LWW) conflict resolution based on `lastUpdated`.
 * If the remote record was modified more recently than the local one
 * the remote copy wins; otherwise the local copy is kept.
 */
class ConflictResolver {

    fun resolveBookConflict(local: Book, remote: Book): Book {
        return if (remote.lastUpdated > local.lastUpdated) remote else local
    }

    fun resolveMemberConflict(local: Member, remote: Member): Member {
        return if (remote.lastUpdated > local.lastUpdated) remote else local
    }

    fun resolveTransactionConflict(local: IssuedBook, remote: IssuedBook): IssuedBook {
        return if (remote.lastUpdated > local.lastUpdated) remote else local
    }

    fun resolveReservationConflict(local: Reservation, remote: Reservation): Reservation {
        return if (remote.lastUpdated > local.lastUpdated) remote else local
    }
}
