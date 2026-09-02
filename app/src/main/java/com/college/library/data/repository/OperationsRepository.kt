package com.college.library.data.repository

import android.content.Context
import com.college.library.data.model.OperationsCollections
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore access for the "operations" collections — gate log, acquisitions,
 * book transfers, serials, ILL requests and the wishlist.
 *
 * These deliberately do NOT go through SQLDelight and the KMP sync engine, the
 * way books and members do. Two reasons:
 *
 *   1. Adding six entities to SQLDelight means schema migrations on an app that
 *      is already deployed, which is a lot of risk for low-volume admin data.
 *   2. The Firestore Android SDK enables offline persistence by default, so
 *      reads are served from the local cache and writes are queued and replayed
 *      when the device reconnects. That already gives these screens the
 *      offline-first behaviour they need.
 *
 * Every document carries the same sync envelope as the core collections
 * (syncId as the document id, collegeId, lastUpdated, deleted, syncStatus), so
 * the desktop and web apps read and write exactly the same records.
 */
@Singleton
class OperationsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val db: FirebaseFirestore? by lazy {
        runCatching { FirebaseFirestore.getInstance() }.getOrNull()
    }

    /** Institution resolved at login. Empty until the user signs in. */
    fun institutionId(): String =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .getString("institution_id", "") ?: ""

    private fun collection(name: String) =
        institutionId().takeIf { it.isNotEmpty() }?.let { instId ->
            db?.collection("institutions")?.document(instId)?.collection(name)
        }

    /**
     * Live stream of a collection, soft-deleted records filtered out.
     *
     * Emits an empty list rather than throwing when the user has no
     * institution yet or Firebase is unavailable, so a screen opened before
     * login shows its empty state instead of crashing.
     */
    fun observe(collectionName: String): Flow<List<Map<String, Any?>>> = callbackFlow {
        val col = collection(collectionName)
        if (col == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration: ListenerRegistration = col.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Permission denied or offline with no cache. Report empty
                // rather than tearing down the screen.
                trySend(emptyList())
                return@addSnapshotListener
            }
            val rows = snapshot?.documents.orEmpty().mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                if (data["deleted"] == true) return@mapNotNull null
                data + mapOf("syncId" to doc.id)
            }
            trySend(rows)
        }

        awaitClose { registration.remove() }
    }

    /**
     * Create or replace a record.
     *
     * The caller passes plain fields; the sync envelope is filled in here so
     * every writer produces the same shape.
     */
    suspend fun save(
        collectionName: String,
        fields: Map<String, Any?>,
        syncId: String? = null,
    ): Result<String> {
        val col = collection(collectionName)
            ?: return Result.failure(IllegalStateException("Not signed in to an institution."))
        val id = syncId ?: (fields["syncId"] as? String)?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString()
        return runCatching {
            col.document(id).set(
                fields + mapOf(
                    "syncId" to id,
                    "collegeId" to institutionId(),
                    "lastUpdated" to System.currentTimeMillis(),
                    "deleted" to false,
                    "syncStatus" to "synced",
                ),
                SetOptions.merge(),
            ).await()
            id
        }
    }

    /** Patch specific fields, refreshing the sync timestamp. */
    suspend fun update(
        collectionName: String,
        syncId: String,
        fields: Map<String, Any?>,
    ): Result<Unit> {
        val col = collection(collectionName)
            ?: return Result.failure(IllegalStateException("Not signed in to an institution."))
        return runCatching {
            col.document(syncId).set(
                fields + mapOf("lastUpdated" to System.currentTimeMillis(), "syncStatus" to "synced"),
                SetOptions.merge(),
            ).await()
            Unit
        }
    }

    /**
     * Soft delete.
     *
     * Never a hard delete: the desktop and web sync engines propagate the
     * `deleted` flag and rely on the document continuing to exist, so removing
     * it outright would let any offline peer resurrect the record.
     */
    suspend fun softDelete(collectionName: String, syncId: String): Result<Unit> =
        update(collectionName, syncId, mapOf("deleted" to true))

    // ── Convenience accessors, so screens name a feature rather than a string ──

    fun observeVisitorLog() = observe(OperationsCollections.VISITOR_LOG)
    fun observePurchaseOrders() = observe(OperationsCollections.PURCHASE_ORDERS)
    fun observeBookTransfers() = observe(OperationsCollections.BOOK_TRANSFERS)
    fun observeSerials() = observe(OperationsCollections.SERIALS)
    fun observeIllRequests() = observe(OperationsCollections.ILL_REQUESTS)
    fun observeWishlist() = observe(OperationsCollections.WISHLIST)
}

// ── Map helpers ───────────────────────────────────────────────────────────────
//
// Firestore returns Long for whole numbers and Double for decimals, and a field
// written by another platform may be missing entirely. These read a value of the
// wanted type or fall back, so a record written by the desktop app in an older
// shape cannot crash a screen.

fun Map<String, Any?>.str(key: String, fallback: String = ""): String =
    (this[key] as? String) ?: fallback

fun Map<String, Any?>.long(key: String, fallback: Long = 0L): Long = when (val v = this[key]) {
    is Long -> v
    is Int -> v.toLong()
    is Double -> v.toLong()
    is String -> v.toLongOrNull() ?: fallback
    else -> fallback
}

fun Map<String, Any?>.int(key: String, fallback: Int = 0): Int = long(key, fallback.toLong()).toInt()

fun Map<String, Any?>.dbl(key: String, fallback: Double = 0.0): Double = when (val v = this[key]) {
    is Double -> v
    is Long -> v.toDouble()
    is Int -> v.toDouble()
    is String -> v.toDoubleOrNull() ?: fallback
    else -> fallback
}

fun Map<String, Any?>.longOrNull(key: String): Long? = when (val v = this[key]) {
    is Long -> v
    is Int -> v.toLong()
    is Double -> v.toLong()
    else -> null
}
