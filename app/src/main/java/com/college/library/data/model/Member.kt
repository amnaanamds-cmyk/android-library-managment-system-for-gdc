package com.college.library.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "members",
    indices = [Index(value = ["memberId"], unique = true)]
)
data class Member(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val memberId: String,
    val name: String,
    val email: String,
    val phone: String,
    val department: String,
    val memberType: String,
    val joinDate: String,
    val expiryDate: String,
    val booksIssued: Int = 0,
    val fatherName: String = "",
    val className: String = "",
    val classNo: String = "",
    val address: String = "",
    val photoUri: String? = null,
    val designation: String = "",
    val bps: String = "",
    val pin: String = ""
)
