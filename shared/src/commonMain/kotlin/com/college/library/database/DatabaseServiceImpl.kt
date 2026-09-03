package com.college.library.database

import com.college.library.data.model.*
import com.college.library.data.DatabaseService
import com.college.library.data.db.LibraryDatabase

class DatabaseServiceImpl(val libraryDatabase: LibraryDatabase) : DatabaseService {
    
    override suspend fun saveBook(book: Book) {
        libraryDatabase.bookQueriesQueries.insertBook(
            syncId = book.syncId,
            isbn = book.isbn,
            accNo = book.accNo,
            title = book.title,
            author = book.author,
            publisher = book.publisher,
            publisherPlace = book.publisherPlace,
            publishDate = book.publishDate,
            edition = book.edition,
            pages = book.pages.toLong(),
            procurement = book.procurement,
            volume = book.volume,
            price = book.price,
            status = book.status,
            isDigital = book.isDigital,
            digitalUrl = book.digitalUrl,
            category = book.category,
            marcData = book.marcData,
            callNumber = book.callNumber,
            authorCutter = book.authorCutter,
            collegeId = book.collegeId,
            syncStatus = book.syncStatus,
            lastUpdated = book.lastUpdated,
            deleted = book.deleted
        )
    }

    override suspend fun saveMember(member: Member) {
        libraryDatabase.memberQueriesQueries.insertMember(
            syncId = member.syncId,
            memberId = member.memberId,
            name = member.name,
            email = member.email,
            phone = member.phone,
            department = member.department,
            memberType = member.memberType,
            joinDate = member.joinDate,
            expiryDate = member.expiryDate,
            booksIssued = member.booksIssued.toLong(),
            fatherName = member.fatherName,
            className = member.className,
            classNo = member.classNo,
            address = member.address,
            photoUri = member.photoUri,
            designation = member.designation,
            bps = member.bps,
            pin = member.pin,
            biometricHash = member.biometricHash,
            biometricEnrolDate = member.biometricEnrolDate,
            biometricLastVerified = member.biometricLastVerified,
            collegeId = member.collegeId,
            syncStatus = member.syncStatus,
            lastUpdated = member.lastUpdated,
            deleted = member.deleted
        )
    }


    override suspend fun saveTransaction(transaction: IssuedBook) {
        libraryDatabase.issuedBookQueriesQueries.insertIssuedBook(
            syncId = transaction.syncId,
            bookId = transaction.bookId,
            bookTitle = transaction.bookTitle,
            bookIsbn = transaction.bookIsbn,
            memberId = transaction.memberId,
            memberName = transaction.memberName,
            memberMemberId = transaction.memberMemberId,
            issueDate = transaction.issueDate,
            dueDate = transaction.dueDate,
            returnDate = transaction.returnDate,
            fine = transaction.fine,
            status = transaction.status,
            lastUpdated = transaction.lastUpdated,
            deleted = transaction.deleted,
            syncStatus = transaction.syncStatus,
            collegeId = transaction.collegeId
        )
    }

    override suspend fun saveReservation(reservation: Reservation) {
        libraryDatabase.reservationQueriesQueries.insert(
            syncId = reservation.syncId,
            bookId = reservation.bookId,
            bookTitle = reservation.bookTitle,
            memberId = reservation.memberId,
            memberName = reservation.memberName,
            reservedDate = reservation.reservedDate,
            status = reservation.status,
            notifiedDate = reservation.notifiedDate,
            lastUpdated = reservation.lastUpdated,
            deleted = reservation.deleted,
            syncStatus = reservation.syncStatus,
            collegeId = reservation.collegeId
        )
    }

    override suspend fun findBookBySyncId(syncId: String): Book? {
        return libraryDatabase.bookQueriesQueries.getBookBySyncId(syncId).executeAsOneOrNull()?.let {
            Book(
                id = it.id,
                syncId = it.syncId,
                isbn = it.isbn,
                accNo = it.accNo,
                title = it.title,
                author = it.author,
                publisher = it.publisher,
                publisherPlace = it.publisherPlace,
                publishDate = it.publishDate,
                edition = it.edition,
                pages = it.pages.toInt(),
                procurement = it.procurement,
                volume = it.volume,
                price = it.price,
                status = it.status,
                isDigital = it.isDigital,
                digitalUrl = it.digitalUrl,
                category = it.category,
                marcData = it.marcData,
                callNumber = it.callNumber,
                authorCutter = it.authorCutter,
                collegeId = it.collegeId,
                syncStatus = it.syncStatus,
                lastUpdated = it.lastUpdated,
                deleted = it.deleted
            )
        }
    }

    override suspend fun findMemberBySyncId(syncId: String): Member? {
        return libraryDatabase.memberQueriesQueries.getMemberBySyncId(syncId).executeAsOneOrNull()?.let {
            Member(
                id = it.id,
                syncId = it.syncId,
                memberId = it.memberId,
                name = it.name,
                email = it.email,
                phone = it.phone,
                department = it.department,
                memberType = it.memberType,
                joinDate = it.joinDate,
                expiryDate = it.expiryDate,
                booksIssued = it.booksIssued.toInt(),
                fatherName = it.fatherName,
                className = it.className,
                classNo = it.classNo,
                address = it.address,
                photoUri = it.photoUri,
                designation = it.designation,
                bps = it.bps,
                pin = it.pin,
                biometricHash = it.biometricHash,
                biometricEnrolDate = it.biometricEnrolDate,
                biometricLastVerified = it.biometricLastVerified,
                collegeId = it.collegeId,
                syncStatus = it.syncStatus,
                lastUpdated = it.lastUpdated,
                deleted = it.deleted
            )
        }
    }

    override suspend fun findTransactionBySyncId(syncId: String): IssuedBook? {
        return libraryDatabase.issuedBookQueriesQueries.getBySyncId(syncId).executeAsOneOrNull()?.let {
            IssuedBook(
                id = it.id,
                syncId = it.syncId,
                bookId = it.bookId,
                bookTitle = it.bookTitle,
                bookIsbn = it.bookIsbn,
                memberId = it.memberId,
                memberName = it.memberName,
                memberMemberId = it.memberMemberId,
                issueDate = it.issueDate,
                dueDate = it.dueDate,
                returnDate = it.returnDate,
                fine = it.fine,
                status = it.status,
                lastUpdated = it.lastUpdated,
                deleted = it.deleted,
                syncStatus = it.syncStatus,
                collegeId = it.collegeId
            )
        }
    }

    override suspend fun findReservationBySyncId(syncId: String): Reservation? {
        return libraryDatabase.reservationQueriesQueries.getBySyncId(syncId).executeAsOneOrNull()?.let {
            Reservation(
                id = it.id,
                syncId = it.syncId,
                bookId = it.bookId,
                bookTitle = it.bookTitle,
                memberId = it.memberId,
                memberName = it.memberName,
                reservedDate = it.reservedDate,
                status = it.status,
                notifiedDate = it.notifiedDate,
                lastUpdated = it.lastUpdated,
                deleted = it.deleted,
                syncStatus = it.syncStatus,
                collegeId = it.collegeId
            )
        }
    }

    override suspend fun getUnsyncedBooks(): List<Book> {
        return libraryDatabase.bookQueriesQueries.getUnsyncedBooks().executeAsList()
            .map {
                Book(
                    id = it.id,
                    syncId = it.syncId,
                    isbn = it.isbn,
                    accNo = it.accNo,
                    title = it.title,
                    author = it.author,
                    publisher = it.publisher,
                    publisherPlace = it.publisherPlace,
                    publishDate = it.publishDate,
                    edition = it.edition,
                    pages = it.pages.toInt(),
                    procurement = it.procurement,
                    volume = it.volume,
                    price = it.price,
                    status = it.status,
                    isDigital = it.isDigital,
                    digitalUrl = it.digitalUrl,
                    category = it.category,
                    marcData = it.marcData,
                    callNumber = it.callNumber,
                    authorCutter = it.authorCutter,
                    collegeId = it.collegeId,
                    syncStatus = it.syncStatus,
                    lastUpdated = it.lastUpdated,
                    deleted = it.deleted
                )
            }
    }

    override suspend fun getUnsyncedMembers(): List<Member> {
        return libraryDatabase.memberQueriesQueries.getUnsyncedMembers().executeAsList()
            .map {
                Member(
                    id = it.id,
                    syncId = it.syncId,
                    memberId = it.memberId,
                    name = it.name,
                    email = it.email,
                    phone = it.phone,
                    department = it.department,
                    memberType = it.memberType,
                    joinDate = it.joinDate,
                    expiryDate = it.expiryDate,
                    booksIssued = it.booksIssued.toInt(),
                    fatherName = it.fatherName,
                    className = it.className,
                    classNo = it.classNo,
                    address = it.address,
                    photoUri = it.photoUri,
                    designation = it.designation,
                    bps = it.bps,
                    pin = it.pin,
                    biometricHash = it.biometricHash,
                    biometricEnrolDate = it.biometricEnrolDate,
                    biometricLastVerified = it.biometricLastVerified,
                    collegeId = it.collegeId,
                    syncStatus = it.syncStatus,
                    lastUpdated = it.lastUpdated,
                    deleted = it.deleted
                )
            }
    }

    override suspend fun getUnsyncedTransactions(): List<IssuedBook> {
        return libraryDatabase.issuedBookQueriesQueries.getUnsyncedIssuedBooks().executeAsList()
            .map {
                IssuedBook(
                    id = it.id,
                    syncId = it.syncId,
                    bookId = it.bookId,
                    bookTitle = it.bookTitle,
                    bookIsbn = it.bookIsbn,
                    memberId = it.memberId,
                    memberName = it.memberName,
                    memberMemberId = it.memberMemberId,
                    issueDate = it.issueDate,
                    dueDate = it.dueDate,
                    returnDate = it.returnDate,
                    fine = it.fine,
                    status = it.status,
                    lastUpdated = it.lastUpdated,
                    deleted = it.deleted,
                    syncStatus = it.syncStatus,
                    collegeId = it.collegeId
                )
            }
    }

    override suspend fun getUnsyncedReservations(): List<Reservation> {
        return libraryDatabase.reservationQueriesQueries.getUnsyncedReservations().executeAsList()
            .map {
                Reservation(
                    id = it.id,
                    syncId = it.syncId,
                    bookId = it.bookId,
                    bookTitle = it.bookTitle,
                    memberId = it.memberId,
                    memberName = it.memberName,
                    reservedDate = it.reservedDate,
                    status = it.status,
                    notifiedDate = it.notifiedDate,
                    lastUpdated = it.lastUpdated,
                    deleted = it.deleted,
                    syncStatus = it.syncStatus,
                    collegeId = it.collegeId
                )
            }
    }


    override suspend fun markAsSynced(syncId: String, entityType: String, serverTimestamp: Long) {
        when (entityType) {
            "books" -> libraryDatabase.bookQueriesQueries.updateTimestamp(serverTimestamp, syncId)
            "members" -> libraryDatabase.memberQueriesQueries.updateTimestamp(serverTimestamp, syncId)
            "issued_books" -> libraryDatabase.issuedBookQueriesQueries.updateTimestamp(serverTimestamp, syncId)
            "reservations" -> libraryDatabase.reservationQueriesQueries.updateTimestamp(serverTimestamp, syncId)
        }
    }

    /**
     * BUG 4 FIX: Retrieve the persisted pull-cursor timestamp from the local DB.
     * We reuse the AppStatsQueries metadata table (key = "last_sync_timestamp").
     * Returns 0L if no timestamp has been stored yet (first run = full pull).
     */
    override suspend fun getLastSyncTimestamp(): Long {
        return runCatching {
            libraryDatabase.appStatsQueriesQueries
                .getStatValue("last_sync_timestamp")
                .executeAsOneOrNull()
                ?.toLongOrNull() ?: 0L
        }.getOrDefault(0L)
    }

    /**
     * BUG 4 FIX: Persist the pull-cursor timestamp to the local DB so it survives
     * app restarts. Uses an upsert into the AppStatsQueries metadata table.
     */
    override suspend fun setLastSyncTimestamp(timestampMillis: Long) {
        runCatching {
            libraryDatabase.appStatsQueriesQueries
                .upsertStat("last_sync_timestamp", timestampMillis.toString())
        }
    }
}
