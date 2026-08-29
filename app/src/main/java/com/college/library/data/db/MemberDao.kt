package com.college.library.data.db

import com.college.library.data.model.Member
import kotlinx.coroutines.flow.Flow

interface MemberDao {
    fun getAllMembers(): Flow<List<Member>>
    fun searchMembers(query: String): Flow<List<Member>>
    suspend fun getMemberById(id: Long): Member?
    fun getMemberByIdFlow(id: Long): Flow<Member?>
    suspend fun loginOpacStudent(memberId: String, pin: String): Member?
    suspend fun insertMember(member: Member)
    suspend fun updateMember(member: Member)
    suspend fun deleteMember(member: Member)
    fun getTopMembers(limit: Int): Flow<List<Member>>
    fun getTotalCount(): Flow<Int>
}
