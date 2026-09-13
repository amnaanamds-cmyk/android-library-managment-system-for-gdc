package com.college.library.data.db

import com.college.library.data.db.adapters.toModel
import com.college.library.data.model.Member
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.college.library.data.db.LibraryDatabase as SQLDelightDb
import java.util.UUID

// Drop-in replacement for the Room MemberDao interface.
class MemberDaoAdapter(private val db: SQLDelightDb) : MemberDao {

    private val queries get() = db.memberQueriesQueries

    override fun getAllMembers(): Flow<List<Member>> =
        queries.getAllMembers().asFlow().mapToList(Dispatchers.IO).map { it.map { row -> row.toModel() } }

    override fun searchMembers(query: String): Flow<List<Member>> =
        queries.searchMembers(query).asFlow().mapToList(Dispatchers.IO).map { it.map { row -> row.toModel() } }

    override suspend fun getMemberById(id: Long): Member? = withContext(Dispatchers.IO) {
        queries.getMemberById(id).executeAsOneOrNull()?.toModel()
    }

    override fun getMemberByIdFlow(id: Long): Flow<Member?> =
        queries.getMemberById(id).asFlow().mapToOneOrNull(Dispatchers.IO).map { it?.toModel() }

    override suspend fun loginOpacStudent(memberId: String, pin: String): Member? = withContext(Dispatchers.IO) {
        queries.loginOpacStudent(memberId, pin).executeAsOneOrNull()?.toModel()
    }

    override suspend fun insertMember(member: Member): Unit = withContext(Dispatchers.IO) {
        val syncId = if (member.syncId.isBlank()) UUID.randomUUID().toString() else member.syncId
        queries.insertMember(
            syncId = syncId,
            memberId = member.memberId, name = member.name,
            email = member.email, phone = member.phone,
            department = member.department, memberType = member.memberType,
            joinDate = member.joinDate, expiryDate = member.expiryDate,
            booksIssued = member.booksIssued.toLong(),
            fatherName = member.fatherName, className = member.className,
            classNo = member.classNo, address = member.address,
            photoUri = member.photoUri, designation = member.designation,
            bps = member.bps, pin = member.pin,
            biometricHash = member.biometricHash,
            biometricEnrolDate = member.biometricEnrolDate,
            biometricLastVerified = member.biometricLastVerified,
            collegeId = member.collegeId,
            syncStatus = "pending", // Always mark as pending — push engine flips to 'synced' after upload
            lastUpdated = System.currentTimeMillis(), deleted = false
        )
        runCatching { com.college.library.data.SyncManager.getSyncService(db).pushChanges() }
        Unit
    }

    override suspend fun updateMember(member: Member): Unit = withContext(Dispatchers.IO) {
        queries.updateMember(
            syncId = member.syncId, memberId = member.memberId, name = member.name,
            email = member.email, phone = member.phone,
            department = member.department, memberType = member.memberType,
            joinDate = member.joinDate, expiryDate = member.expiryDate,
            booksIssued = member.booksIssued.toLong(),
            fatherName = member.fatherName, className = member.className,
            classNo = member.classNo, address = member.address,
            photoUri = member.photoUri, designation = member.designation,
            bps = member.bps, pin = member.pin,
            biometricHash = member.biometricHash,
            biometricEnrolDate = member.biometricEnrolDate,
            biometricLastVerified = member.biometricLastVerified,
            collegeId = member.collegeId,
            syncStatus = "pending", // Mark as pending so the push engine re-uploads this member
            lastUpdated = System.currentTimeMillis(), deleted = member.deleted,
            id = member.id
        )
        runCatching { com.college.library.data.SyncManager.getSyncService(db).pushChanges() }
        Unit
    }

    override suspend fun deleteMember(member: Member): Unit = withContext(Dispatchers.IO) {
        // Soft delete so the tombstone reaches Firestore — see BookDaoAdapter.
        queries.softDeleteMember(lastUpdated = System.currentTimeMillis(), id = member.id)
        runCatching { com.college.library.data.SyncManager.getSyncService(db).pushChanges() }
        Unit
    }

    override fun getTopMembers(limit: Int): Flow<List<Member>> =
        queries.getTopMembers(limit.toLong()).asFlow().mapToList(Dispatchers.IO).map { it.map { row -> row.toModel() } }

    override fun getTotalCount(): Flow<Int> =
        queries.getTotalCount().asFlow().mapToOneOrNull(Dispatchers.IO).map { it?.toInt() ?: 0 }
}
