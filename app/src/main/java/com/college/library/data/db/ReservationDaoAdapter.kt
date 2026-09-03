package com.college.library.data.db

import com.college.library.data.db.adapters.toModel
import com.college.library.data.model.Reservation
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.college.library.data.db.LibraryDatabase as SQLDelightDb
import java.util.UUID

// Drop-in replacement for the Room ReservationDao interface.
class ReservationDaoAdapter(private val db: SQLDelightDb) : ReservationDao {

    private val queries get() = db.reservationQueriesQueries

    override suspend fun insert(reservation: Reservation): Unit = withContext(Dispatchers.IO) {
        val syncId = if (reservation.syncId.isBlank()) UUID.randomUUID().toString() else reservation.syncId
        queries.insert(
            syncId = syncId,
            bookId = reservation.bookId, bookTitle = reservation.bookTitle,
            memberId = reservation.memberId, memberName = reservation.memberName,
            reservedDate = reservation.reservedDate, status = reservation.status,
            notifiedDate = reservation.notifiedDate,
            lastUpdated = System.currentTimeMillis(), deleted = false,
            syncStatus = "pending", // Mark pending so push engine uploads this reservation
            collegeId = reservation.collegeId
        )
        runCatching { com.college.library.data.SyncManager.getSyncService(db).pushChanges() }
        Unit
    }

    override fun getByBook(bookId: Long): Flow<List<Reservation>> =
        queries.getByBook(bookId).asFlow().mapToList(Dispatchers.IO).map { it.map { row -> row.toModel() } }

    override fun getByMember(memberId: Long): Flow<List<Reservation>> =
        queries.getByMember(memberId).asFlow().mapToList(Dispatchers.IO).map { it.map { row -> row.toModel() } }

    override fun getAllPending(): Flow<List<Reservation>> =
        queries.getAllPending().asFlow().mapToList(Dispatchers.IO).map { it.map { row -> row.toModel() } }

    override suspend fun updateStatus(id: Long, status: String): Unit = withContext(Dispatchers.IO) {
        queries.updateStatus(status = status, id = id)
        runCatching { com.college.library.data.SyncManager.getSyncService(db).pushChanges() }
        Unit
    }

    override suspend fun updateNotifiedDate(id: Long, date: String): Unit = withContext(Dispatchers.IO) {
        queries.updateNotifiedDate(date = date, id = id)
        runCatching { com.college.library.data.SyncManager.getSyncService(db).pushChanges() }
        Unit
    }

    override suspend fun delete(reservation: Reservation): Unit = withContext(Dispatchers.IO) {
        queries.delete(reservation.id)
        runCatching { com.college.library.data.SyncManager.getSyncService(db).pushChanges() }
        Unit
    }

    override fun getReservationCount(bookId: Long): Flow<Int> =
        queries.getReservationCount(bookId).asFlow().mapToOneOrNull(Dispatchers.IO).map { it?.toInt() ?: 0 }

    override suspend fun getFirstPendingForBook(bookId: Long): Reservation? = withContext(Dispatchers.IO) {
        queries.getFirstPendingForBook(bookId).executeAsOneOrNull()?.toModel()
    }
}
