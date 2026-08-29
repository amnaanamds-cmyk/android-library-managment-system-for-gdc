package com.college.library.data.db.adapters

import com.college.library.data.db.Books
import com.college.library.data.db.Members
import com.college.library.data.db.Issued_books
import com.college.library.data.db.Reservations
import com.college.library.data.model.Book
import com.college.library.data.model.Member
import com.college.library.data.model.IssuedBook
import com.college.library.data.model.Reservation

fun Books.toModel() = Book(
    syncId = syncId,
    id = id,
    isbn = isbn,
    accNo = accNo,
    title = title,
    author = author,
    publisher = publisher,
    publisherPlace = publisherPlace,
    publishDate = publishDate,
    edition = edition,
    pages = pages.toInt(),
    procurement = procurement,
    volume = volume,
    price = price,
    status = status,
    isDigital = isDigital,
    digitalUrl = digitalUrl,
    category = category,
    marcData = marcData,
    lastUpdated = lastUpdated,
    deleted = deleted
)

fun Members.toModel() = Member(
    syncId = syncId,
    id = id,
    memberId = memberId,
    name = name,
    email = email,
    phone = phone,
    department = department,
    memberType = memberType,
    joinDate = joinDate,
    expiryDate = expiryDate,
    booksIssued = booksIssued.toInt(),
    fatherName = fatherName,
    className = className,
    classNo = classNo,
    address = address,
    photoUri = photoUri,
    designation = designation,
    bps = bps,
    pin = pin,
    lastUpdated = lastUpdated,
    deleted = deleted
)

fun Issued_books.toModel() = IssuedBook(
    syncId = syncId,
    id = id,
    bookId = bookId,
    bookTitle = bookTitle,
    bookIsbn = bookIsbn,
    memberId = memberId,
    memberName = memberName,
    memberMemberId = memberMemberId,
    issueDate = issueDate,
    dueDate = dueDate,
    returnDate = returnDate,
    fine = fine,
    status = status,
    lastUpdated = lastUpdated,
    deleted = deleted
)

fun Reservations.toModel() = Reservation(
    syncId = syncId,
    id = id,
    bookId = bookId,
    bookTitle = bookTitle,
    memberId = memberId,
    memberName = memberName,
    reservedDate = reservedDate,
    status = status,
    notifiedDate = notifiedDate,
    lastUpdated = lastUpdated,
    deleted = deleted
)
