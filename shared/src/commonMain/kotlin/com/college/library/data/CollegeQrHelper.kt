package com.college.library.data

import com.college.library.data.model.CollegeLink
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Builds and parses the QR link payload used to link a college to the
 * director network.
 *
 * Supports two formats:
 * 1. Pipe-separated string (used by mobile app): NEXLIB_LINK|collegeId|role
 * 2. JSON (used for detail linking): {"collegeId":"...","collegeName":"..."}
 */
object CollegeQrHelper {

    private val json = Json { ignoreUnknownKeys = true }

    /** Generates the legacy pipe-separated string for mobile app linking. */
    fun generateMobilePayload(collegeId: String, role: String = "Librarian"): String {
        return "NEXLIB_LINK|$collegeId|$role"
    }

    /** Generates the JSON payload for desktop/web detail views. */
    fun generatePayload(collegeId: String, collegeName: String, linkedAt: Long = nowMillis()): String {
        return json.encodeToString(CollegeLink(collegeId = collegeId, collegeName = collegeName, linkedAt = linkedAt))
    }

    fun parse(payload: String): CollegeLink? {
        if (payload.startsWith("NEXLIB_LINK|")) {
            val parts = payload.split("|")
            if (parts.size >= 2) {
                return CollegeLink(collegeId = parts[1], collegeName = "Linked College", linkedAt = nowMillis())
            }
        }
        return runCatching { json.decodeFromString<CollegeLink>(payload) }.getOrNull()
    }

    private fun nowMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}
