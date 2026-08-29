package com.college.library.data

/**
 * Cross-platform guard for Firebase availability.
 *
 * A desktop or demo build without a configured Firebase project
 * (no google-services.json / no FirebaseOptions) must keep working
 * in offline mode instead of crashing when a service is constructed.
 */
object FirebaseAvailability {
    /** True when a Firebase project is available at runtime. */
    val isInitialized: Boolean
        get() = platformIsFirebaseInitialized()
}

/** Platform-specific Firebase probe. */
internal expect fun platformIsFirebaseInitialized(): Boolean
