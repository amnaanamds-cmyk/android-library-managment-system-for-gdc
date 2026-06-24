package com.college.library.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reservations")
data class Reservation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val bookTitle: String,
    val memberId: Long,
    val memberName: String,
    val reservedDate: String,
    val status: String = "Pending",
    val notifiedDate: String? = null
)
