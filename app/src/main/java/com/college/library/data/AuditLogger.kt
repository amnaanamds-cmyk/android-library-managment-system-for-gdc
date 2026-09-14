package com.college.library.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Accountability trail for actions taken on this device.
 *
 * Android previously wrote no audit entries at all, so a book or member
 * deleted from a phone left no record of who did it — on a system holding
 * several colleges' patron data that is the difference between an incident
 * you can investigate and one you cannot.
 *
 * Entries go to `/institutions/{collegeId}/audit_log`, the same collection
 * and field names the desktop app writes (userEmail / action / detail /
 * timestamp / timestampStr), so both platforms' entries read as one trail.
 *
 * Logging must never block or fail the action it is recording: a librarian
 * whose audit write is denied still has to be able to run the library, so
 * every failure here is swallowed. The trail is append-only by security
 * rule — staff may create entries but only an admin may amend or delete
 * them — so an entry that lands cannot be quietly rewritten later.
 */
object AuditLogger {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** Fire-and-forget. Safe to call from the UI thread. */
    fun log(context: Context, action: String, detail: String) {
        val institutionId = context
            .getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .getString("institution_id", "")
            .orEmpty()
        if (institutionId.isEmpty()) return

        val email = runCatching { FirebaseAuth.getInstance().currentUser?.email }
            .getOrNull()
            .orEmpty()
            .ifEmpty { "unknown" }

        val now = System.currentTimeMillis()
        val entry = mapOf(
            "userEmail" to email,
            "action" to action,
            "detail" to detail,
            "timestamp" to now,
            "timestampStr" to stamp.format(Date(now)),
            "platform" to "android",
        )

        scope.launch {
            runCatching {
                FirebaseFirestore.getInstance()
                    .collection("institutions")
                    .document(institutionId)
                    .collection("audit_log")
                    .add(entry)
            }
        }
    }
}
