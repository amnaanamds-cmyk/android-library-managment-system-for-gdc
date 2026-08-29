package com.college.library.data

/**
 * Android Firebase probe. On Android the default app is initialized
 * automatically by FirebaseInitProvider using google-services.json.
 * getInstance() throws IllegalStateException when no app is configured,
 * which is exactly the signal we need for the offline fallback.
 */
internal actual fun platformIsFirebaseInitialized(): Boolean =
    runCatching { com.google.firebase.FirebaseApp.getInstance() }.isSuccess
