package com.college.library.data

import com.college.library.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class RepositoryImpl : Repository {
    override fun getBooks(): Flow<List<Book>> = emptyFlow()
    override fun getMembers(): Flow<List<Member>> = emptyFlow()
    override fun getTransactions(): Flow<List<Transaction>> = emptyFlow()
    override fun getReservations(): Flow<List<Reservation>> = emptyFlow()

    override suspend fun upsertBook(book: Book) {
        // Implementation
    }

    override suspend fun upsertMember(member: Member) {
        // Implementation
    }

    override suspend fun upsertTransaction(transaction: Transaction) {
        // Implementation
    }

    override suspend fun upsertReservation(reservation: Reservation) {
        // Implementation
    }
}
