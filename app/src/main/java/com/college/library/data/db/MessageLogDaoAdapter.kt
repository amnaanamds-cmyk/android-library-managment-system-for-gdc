package com.college.library.data.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.college.library.data.model.MessageLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.college.library.data.db.LibraryDatabase as SQLDelightDb

class MessageLogDaoAdapter(db: SQLDelightDb) : MessageLogDao {
    private val queries = db.messageLogQueriesQueries

    override fun getAllMessageLogs(): Flow<List<MessageLog>> {
        return queries.getAllMessageLogs().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map {
                MessageLog(
                    id = it.id,
                    memberId = it.memberId,
                    memberName = it.memberName,
                    memberPhone = it.memberPhone,
                    bookTitle = it.bookTitle,
                    channel = it.channel,
                    message = it.message,
                    sentDate = it.sentDate,
                    status = it.status,
                    lastUpdated = it.lastUpdated
                )
            }
        }
    }

    override fun getMessageLogsByDate(date: String): Flow<List<MessageLog>> {
        return queries.getMessageLogsByDate(date).asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map {
                MessageLog(
                    id = it.id,
                    memberId = it.memberId,
                    memberName = it.memberName,
                    memberPhone = it.memberPhone,
                    bookTitle = it.bookTitle,
                    channel = it.channel,
                    message = it.message,
                    sentDate = it.sentDate,
                    status = it.status,
                    lastUpdated = it.lastUpdated
                )
            }
        }
    }

    override suspend fun insertMessageLog(log: MessageLog) {
        queries.insertMessageLog(
            memberId = log.memberId,
            memberName = log.memberName,
            memberPhone = log.memberPhone,
            bookTitle = log.bookTitle,
            channel = log.channel,
            message = log.message,
            sentDate = log.sentDate,
            status = log.status,
            lastUpdated = log.lastUpdated
        )
    }

    override suspend fun wasMessageSentToday(
        memberId: Long,
        bookTitle: String,
        channel: String,
        date: String
    ): Boolean = withContext(Dispatchers.IO) {
        queries.wasMessageSentToday(memberId, bookTitle, channel, date).executeAsOne() > 0
    }
}
