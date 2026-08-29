package com.college.library.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore

/**
 * JVM (desktop) Firebase probe.
 */
private var _initialized: Boolean = false

fun initializeFirebaseOnJvm() {
    // Attempt to probe if Firebase is actually usable by checking if an app exists.
    // GitLive Firebase JVM usually needs a manual initialization of the Java SDK.
    // For now, we use this flag as a gate.
    _initialized = true
}

internal actual fun platformIsFirebaseInitialized(): Boolean {
    if (!_initialized) return false
    // On JVM, GitLive doesn't have a direct 'isInitialized' check without catching exceptions.
    return try {
        // Just a probe, doesn't actually hit the network.
        // If this throws, Firebase is not configured.
        val db = Firebase.firestore
        true
    } catch (e: Throwable) {
        false
    }
}
