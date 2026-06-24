package com.college.library.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.college.library.data.model.Reservation
import kotlinx.coroutines.flow.Flow

@Dao
interface ReservationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reservation: Reservation)

    @Query("SELECT * FROM reservations WHERE bookId = :bookId ORDER BY reservedDate ASC")
    fun getByBook(bookId: Long): Flow<List<Reservation>>

    @Query("SELECT * FROM reservations WHERE memberId = :memberId ORDER BY reservedDate DESC")
    fun getByMember(memberId: Long): Flow<List<Reservation>>

    @Query("SELECT * FROM reservations WHERE status = 'Pending' ORDER BY reservedDate ASC")
    fun getAllPending(): Flow<List<Reservation>>

    @Query("UPDATE reservations SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE reservations SET notifiedDate = :date WHERE id = :id")
    suspend fun updateNotifiedDate(id: Long, date: String)

    @Delete
    suspend fun delete(reservation: Reservation)

    @Query("SELECT COUNT(*) FROM reservations WHERE bookId = :bookId AND status = 'Pending'")
    fun getReservationCount(bookId: Long): Flow<Int>

    @Query("SELECT * FROM reservations WHERE bookId = :bookId AND status = 'Pending' ORDER BY reservedDate ASC LIMIT 1")
    suspend fun getFirstPendingForBook(bookId: Long): Reservation?
}
