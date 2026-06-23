package com.college.library.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "issued_books",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Member::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("bookId"),
        Index("memberId")
    ]
)
data class IssuedBook(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val bookTitle: String,
    val bookIsbn: String,
    val memberId: Long,
    val memberName: String,
    val memberMemberId: String,
    val issueDate: String,
    val dueDate: String,
    val returnDate: String? = null,
    val fine: Double = 0.0,
    val status: String = "Issued"
)
