package com.college.library.data.db

import com.college.library.data.model.MessageLog
import kotlinx.coroutines.flow.Flow

interface MessageLogDao {
    fun getAllMessageLogs(): Flow<List<MessageLog>>
    fun getMessageLogsByDate(date: String): Flow<List<MessageLog>>
    suspend fun insertMessageLog(log: MessageLog)
    suspend fun wasMessageSentToday(memberId: Long, bookTitle: String, channel: String, date: String): Boolean
}
