package com.college.library.data

import com.college.library.data.model.*

interface DatabaseService {
    suspend fun saveBook(book: Book)
    suspend fun saveMember(member: Member)
    suspend fun saveTransaction(transaction: IssuedBook)
    suspend fun saveReservation(reservation: Reservation)

    suspend fun findBookBySyncId(syncId: String): Book?
    suspend fun findMemberBySyncId(syncId: String): Member?
    suspend fun findTransactionBySyncId(syncId: String): IssuedBook?
    suspend fun findReservationBySyncId(syncId: String): Reservation?

    suspend fun getUnsyncedBooks(): List<Book>
    suspend fun getUnsyncedMembers(): List<Member>
    suspend fun getUnsyncedTransactions(): List<IssuedBook>
    suspend fun getUnsyncedReservations(): List<Reservation>

    suspend fun markAsSynced(syncId: String, entityType: String, serverTimestamp: Long)

    /**
     * BUG 4 FIX: Persist the last-successful-pull cursor timestamp so it survives
     * app restarts. Without this, lastSyncTimestamp resets to 0 on every cold start
     * and the incremental pull re-fetches all documents from Firestore.
     */
    suspend fun getLastSyncTimestamp(): Long
    suspend fun setLastSyncTimestamp(timestampMillis: Long)
}
