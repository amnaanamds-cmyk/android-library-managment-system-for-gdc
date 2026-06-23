package com.college.library.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.college.library.data.model.Member
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members ORDER BY id DESC")
    fun getAllMembers(): Flow<List<Member>>

    @Query("SELECT * FROM members WHERE name LIKE '%' || :query || '%' OR memberId LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%' ORDER BY id DESC")
    fun searchMembers(query: String): Flow<List<Member>>

    @Query("SELECT * FROM members WHERE id = :id LIMIT 1")
    suspend fun getMemberById(id: Long): Member?

    @Query("SELECT * FROM members WHERE id = :id LIMIT 1")
    fun getMemberByIdFlow(id: Long): Flow<Member?>

    @Query("SELECT * FROM members WHERE memberId = :memberId AND pin = :pin LIMIT 1")
    suspend fun loginOpacStudent(memberId: String, pin: String): Member?

    @Insert
    suspend fun insertMember(member: Member)

    @Update
    suspend fun updateMember(member: Member)

    @Delete
    suspend fun deleteMember(member: Member)

    @Query("SELECT * FROM members ORDER BY booksIssued DESC LIMIT :limit")
    fun getTopMembers(limit: Int): Flow<List<Member>>

    @Query("SELECT COUNT(*) FROM members")
    fun getTotalCount(): Flow<Int>
}
