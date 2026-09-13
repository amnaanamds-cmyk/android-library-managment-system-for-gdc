package com.college.library.data

import com.college.library.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

interface SyncService {
    var currentInstitutionId: String

    /** Display name of the active institution, published with the directorate snapshot. */
    var currentInstitutionName: String

    /** Live sync status consumed by the shared status indicator (green / amber / red). */
    val status: StateFlow<SyncStatus>

    /** Starts Firestore snapshot listeners on the given [scope] and applies remote changes. */
    fun startRealtimeSync(scope: CoroutineScope)

    /** Cancels all real-time listeners. */
    fun stopRealtimeSync()

    /** Pushes local unsynced records to Firestore (ebooks are isolated and cannot block the rest). */
    suspend fun pushChanges()

    /** Pulls remote records newer than the last sync timestamp and applies them locally. */
    suspend fun pullChanges()

    /** Convenience: pull then push. */
    suspend fun startFullSync()

    /**
     * Publish this college's aggregate snapshot to the directorate registry.
     * Called automatically after each real-time batch; [force] bypasses the
     * republish interval for an explicit "make me current" request.
     */
    suspend fun publishDirectorateSnapshot(force: Boolean = false)
}

class SyncServiceImpl(
    private val firestoreService: FirestoreService,
    private val databaseService: DatabaseService,
    private val conflictResolver: ConflictResolver
) : SyncService {

    // FIX: No more hardcoded "gdc11" default. This must be explicitly set from
    // AuthViewModel.institutionId right after login/onboarding completes — see
    // MainActivity.kt. Every startRealtimeSync/pushChanges/pullChanges call
    // below already guards on isEmpty(), so leaving this blank until login is
    // safe: sync simply stays idle instead of silently talking to the wrong
    // institution's data.
    override var currentInstitutionId: String = ""
    override var currentInstitutionName: String = ""

    // NotLinked, not Offline: before login there is no institution to sync
    // with, which is not the same thing as having lost the network.
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.NotLinked)
    override val status: StateFlow<SyncStatus> get() = _status

    // BUG 4 FIX: lastSyncTimestamp is now loaded from the local DB on startup
    // (via loadLastSyncTimestamp) so it persists across app restarts instead of
    // always resetting to 0 and re-fetching every document from Firestore.
    private var lastSyncTimestamp: Long = 0L
    private var realtimeJob: Job? = null
    private val pushMutex = Mutex()

    // Latest records seen on each real-time stream. The directorate snapshot is
    // built from these rather than from extra Firestore queries, so publishing
    // the rollup costs no additional document reads.
    private var latestBooks: List<Book> = emptyList()
    private var latestEbooks: List<Book> = emptyList()
    private var latestMembers: List<Member> = emptyList()
    private var latestIssues: List<IssuedBook> = emptyList()
    private var latestReservations: List<Reservation> = emptyList()

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    // ─────────────────────────────────────────────────────────────
    // Real-time engine
    // ─────────────────────────────────────────────────────────────

    override fun startRealtimeSync(scope: CoroutineScope) {
        if (currentInstitutionId.isEmpty()) {
            _status.value = SyncStatus.NotLinked
            return
        }
        stopRealtimeSync()
        if (!FirebaseAvailability.isInitialized) {
            _status.value = SyncStatus.Offline
            return
        }

        val handler = CoroutineExceptionHandler { _, throwable ->
            _status.value = SyncStatus.Error("Sync engine failure")
        }

        realtimeJob = scope.launch(handler) {
            try {
                // BUG 4 FIX: Load persisted timestamp from the local DB before starting
                // listeners so incremental pull picks up from where we left off.
                lastSyncTimestamp = try { databaseService.getLastSyncTimestamp() } catch (e: Exception) { 0L }

                // Immediately attempt pushing any local pending changes on start
                launch { try { pushChanges() } catch (e: Exception) {} }
                launch { watchStream({ firestoreService.observeBooks(currentInstitutionId) }, ::onBooksStream) }
                launch { watchStream({ firestoreService.observeEbooks(currentInstitutionId) }, ::onEbooksStream) }
                launch { watchStream({ firestoreService.observeMembers(currentInstitutionId) }, ::onMembersStream) }
                launch { watchStream({ firestoreService.observeIssues(currentInstitutionId) }, ::onIssuesStream) }
                launch { watchStream({ firestoreService.observeReservations(currentInstitutionId) }, ::onReservationsStream) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _status.value = SyncStatus.Error("Sync setup failed")
            }
        }
    }

    override fun stopRealtimeSync() {
        realtimeJob?.cancel()
        realtimeJob = null
    }

    /**
     * Collects a Firestore snapshot stream forever, reconnecting with a
     * backoff delay whenever the stream fails (e.g. device goes offline).
     */
    private suspend fun <T> watchStream(stream: suspend () -> Flow<List<T>>, applyBatch: suspend (List<T>) -> Unit) {
        try {
            val flow = try {
                stream()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            } ?: return

            flow
                .retryWhen { cause, _ ->
                    if (cause is CancellationException) {
                        false
                    } else {
                        _status.value = SyncStatus.Offline
                        delay(5000)
                        true
                    }
                }
                .collect { records ->
                    _status.value = SyncStatus.Syncing
                    try {
                        applyBatch(records)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                    _status.value = SyncStatus.Synced
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = SyncStatus.Error("Real-time stream failed")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Applying remote records in batch (LWW conflict resolution)
    // ─────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────
    // Stream handlers: persist the batch, then refresh the aggregate
    // snapshot the directorate portal reads.
    // ─────────────────────────────────────────────────────────────

    private suspend fun onBooksStream(records: List<Book>) {
        latestBooks = records
        applyBooksBatch(records)
        publishDirectorateSnapshot()
    }

    private suspend fun onEbooksStream(records: List<Book>) {
        latestEbooks = records
        applyBooksBatch(records)
        publishDirectorateSnapshot()
    }

    private suspend fun onMembersStream(records: List<Member>) {
        latestMembers = records
        applyMembersBatch(records)
        publishDirectorateSnapshot()
    }

    private suspend fun onIssuesStream(records: List<IssuedBook>) {
        latestIssues = records
        applyTransactionsBatch(records)
        publishDirectorateSnapshot()
    }

    private suspend fun onReservationsStream(records: List<Reservation>) {
        latestReservations = records
        applyReservationsBatch(records)
        publishDirectorateSnapshot()
    }

    override suspend fun publishDirectorateSnapshot(force: Boolean) {
        if (currentInstitutionId.isEmpty()) return
        // Never let a registry failure disturb the sync engine: the rollup is
        // reporting, and a college must be able to run its library without it.
        try {
            val today = Clock.System.now()
                .toEpochMilliseconds()
                .let { millis -> isoDate(millis) }

            DirectorateRegistry.publish(
                collegeId = currentInstitutionId,
                snapshot = DirectorateRegistry.buildSnapshot(
                    collegeId = currentInstitutionId,
                    name = currentInstitutionName.ifEmpty { currentInstitutionId },
                    books = latestBooks,
                    ebooks = latestEbooks,
                    members = latestMembers,
                    issues = latestIssues,
                    reservations = latestReservations,
                    today = today,
                ),
                force = force,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    /** Today's date as "YYYY-MM-DD" in UTC, matching the stored dueDate format. */
    private fun isoDate(epochMillis: Long): String {
        val days = epochMillis / 86_400_000L
        // Civil-from-days (Howard Hinnant's algorithm), shifted to an era
        // starting 0000-03-01 so leap years fall at the end of each cycle.
        val z = days + 719_468L
        val era = (if (z >= 0) z else z - 146_096L) / 146_097L
        val doe = z - era * 146_097L
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val year = if (m <= 2) y + 1 else y
        return "$year-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }

    private suspend fun applyBooksBatch(records: List<Book>) {
        if (records.isEmpty()) return
        val unsyncedSet = try { databaseService.getUnsyncedBooks().map { it.syncId }.toSet() } catch (e: Exception) { emptySet() }
        for (remote in records) {
            val local = databaseService.findBookBySyncId(remote.syncId)
            if (local == null) {
                databaseService.saveBook(remote)
            } else {
                if (local.lastUpdated == remote.lastUpdated && local.syncStatus == remote.syncStatus) continue
                val isUnsynced = unsyncedSet.contains(remote.syncId)
                if (isUnsynced && local.lastUpdated >= remote.lastUpdated) {
                    continue
                }
                val winner = conflictResolver.resolveBookConflict(local, remote)
                if (winner == remote) databaseService.saveBook(remote)
            }
        }
    }

    private suspend fun applyMembersBatch(records: List<Member>) {
        if (records.isEmpty()) return
        val unsyncedSet = try { databaseService.getUnsyncedMembers().map { it.syncId }.toSet() } catch (e: Exception) { emptySet() }
        for (remote in records) {
            val local = databaseService.findMemberBySyncId(remote.syncId)
            if (local == null) {
                databaseService.saveMember(remote)
            } else {
                if (local.lastUpdated == remote.lastUpdated && local.syncStatus == remote.syncStatus) continue
                val isUnsynced = unsyncedSet.contains(remote.syncId)
                if (isUnsynced && local.lastUpdated >= remote.lastUpdated) {
                    continue
                }
                val winner = conflictResolver.resolveMemberConflict(local, remote)
                if (winner == remote) databaseService.saveMember(remote)
            }
        }
    }

    private suspend fun applyTransactionsBatch(records: List<IssuedBook>) {
        if (records.isEmpty()) return
        val unsyncedSet = try { databaseService.getUnsyncedTransactions().map { it.syncId }.toSet() } catch (e: Exception) { emptySet() }
        for (remote in records) {
            val local = databaseService.findTransactionBySyncId(remote.syncId)
            if (local == null) {
                databaseService.saveTransaction(remote)
            } else {
                if (local.lastUpdated == remote.lastUpdated && local.syncStatus == remote.syncStatus) continue
                val isUnsynced = unsyncedSet.contains(remote.syncId)
                if (isUnsynced && local.lastUpdated >= remote.lastUpdated) {
                    continue
                }
                val winner = conflictResolver.resolveTransactionConflict(local, remote)
                if (winner == remote) databaseService.saveTransaction(remote)
            }
        }
    }

    private suspend fun applyReservationsBatch(records: List<Reservation>) {
        if (records.isEmpty()) return
        val unsyncedSet = try { databaseService.getUnsyncedReservations().map { it.syncId }.toSet() } catch (e: Exception) { emptySet() }
        for (remote in records) {
            val local = databaseService.findReservationBySyncId(remote.syncId)
            if (local == null) {
                databaseService.saveReservation(remote)
            } else {
                if (local.lastUpdated == remote.lastUpdated && local.syncStatus == remote.syncStatus) continue
                val isUnsynced = unsyncedSet.contains(remote.syncId)
                if (isUnsynced && local.lastUpdated >= remote.lastUpdated) {
                    continue
                }
                val winner = conflictResolver.resolveReservationConflict(local, remote)
                if (winner == remote) databaseService.saveReservation(remote)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Push (local -> Firestore)
    // ─────────────────────────────────────────────────────────────

    override suspend fun pushChanges() {
        if (currentInstitutionId.isEmpty()) {
            _status.value = SyncStatus.NotLinked
            return
        }
        if (!FirebaseAvailability.isInitialized) {
            _status.value = SyncStatus.Offline
            return
        }
        pushMutex.withLock {
            try {
                _status.value = SyncStatus.Syncing
                var failed = false

                // Regular (printed) books.
                val unsyncedBooks = try { databaseService.getUnsyncedBooks() } catch (e: Exception) { emptyList() }

                for (book in unsyncedBooks.filter { !it.isDigital }) {
                    try {
                        firestoreService.uploadBook(currentInstitutionId, book)
                        databaseService.markAsSynced(book.syncId, "books", nowMillis())
                    } catch (e: Exception) {
                        failed = true
                    }
                }

                // Ebooks sync to their own collection and are isolated:
                // a failure here never blocks books/members/circulation.
                for (ebook in unsyncedBooks.filter { it.isDigital }) {
                    try {
                        firestoreService.uploadEbook(currentInstitutionId, ebook)
                        databaseService.markAsSynced(ebook.syncId, "books", nowMillis())
                    } catch (e: Exception) {
                        failed = true
                    }
                }

                val unsyncedMembers = try { databaseService.getUnsyncedMembers() } catch (e: Exception) { emptyList() }
                for (member in unsyncedMembers) {
                    try {
                        firestoreService.uploadMember(currentInstitutionId, member)
                        databaseService.markAsSynced(member.syncId, "members", nowMillis())
                    } catch (e: Exception) {
                        failed = true
                    }
                }

                val unsyncedTransactions = try { databaseService.getUnsyncedTransactions() } catch (e: Exception) { emptyList() }
                for (transaction in unsyncedTransactions) {
                    try {
                        firestoreService.uploadIssue(currentInstitutionId, transaction)
                        databaseService.markAsSynced(transaction.syncId, "issued_books", nowMillis())
                    } catch (e: Exception) {
                        failed = true
                    }
                }

                val unsyncedReservations = try { databaseService.getUnsyncedReservations() } catch (e: Exception) { emptyList() }
                for (reservation in unsyncedReservations) {
                    try {
                        firestoreService.uploadReservation(currentInstitutionId, reservation)
                        databaseService.markAsSynced(reservation.syncId, "reservations", nowMillis())
                    } catch (e: Exception) {
                        failed = true
                    }
                }

                _status.value = if (failed) {
                    SyncStatus.Error("Some records could not be pushed")
                } else {
                    SyncStatus.Synced
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _status.value = SyncStatus.Error("Push failed")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Pull (Firestore -> local)
    // ─────────────────────────────────────────────────────────────

    override suspend fun pullChanges() {
        if (currentInstitutionId.isEmpty()) {
            _status.value = SyncStatus.NotLinked
            return
        }
        if (!FirebaseAvailability.isInitialized) {
            _status.value = SyncStatus.Offline
            return
        }
        try {
            _status.value = SyncStatus.Syncing

            // BUG 4 FIX: Always load the persisted timestamp first so that a
            // pull triggered programmatically (e.g. from startFullSync) also
            // picks up from the correct cursor, not from a stale in-memory value.
            lastSyncTimestamp = databaseService.getLastSyncTimestamp()

            val books = firestoreService.fetchBooks(currentInstitutionId, lastSyncTimestamp)
            if (books.isNotEmpty()) applyBooksBatch(books)

            val ebooks = firestoreService.fetchEbooks(currentInstitutionId, lastSyncTimestamp)
            if (ebooks.isNotEmpty()) applyBooksBatch(ebooks)

            val members = firestoreService.fetchMembers(currentInstitutionId, lastSyncTimestamp)
            if (members.isNotEmpty()) applyMembersBatch(members)

            val transactions = firestoreService.fetchIssues(currentInstitutionId, lastSyncTimestamp)
            if (transactions.isNotEmpty()) applyTransactionsBatch(transactions)

            val reservations = firestoreService.fetchReservations(currentInstitutionId, lastSyncTimestamp)
            if (reservations.isNotEmpty()) applyReservationsBatch(reservations)

            val newTimestamp = nowMillis()
            // BUG 4 FIX: Persist the new timestamp to the local DB so it survives restarts.
            databaseService.setLastSyncTimestamp(newTimestamp)
            lastSyncTimestamp = newTimestamp
            _status.value = SyncStatus.Synced
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = SyncStatus.Error("Pull failed")
        }
    }

    override suspend fun startFullSync() {
        pullChanges()
        pushChanges()
    }
}