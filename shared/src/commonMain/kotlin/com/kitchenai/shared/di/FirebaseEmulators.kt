package com.kitchenai.shared.di

/**
 * Where a debug build finds `firebase emulators:start`. Ports default to `firebase.json`; only
 * Firestore's is configurable, because 8080 is the one most often taken on a laptop.
 */
data class FirebaseEmulators(
    val host: String,
    val firestorePort: Int = DEFAULT_FIRESTORE_PORT,
    val authPort: Int = DEFAULT_AUTH_PORT,
    val functionsPort: Int = DEFAULT_FUNCTIONS_PORT,
) {
    companion object {
        const val DEFAULT_FIRESTORE_PORT: Int = 8080
        const val DEFAULT_AUTH_PORT: Int = 9099
        const val DEFAULT_FUNCTIONS_PORT: Int = 5001

        /** A blank host is production; a port that is not a port falls back to the default. */
        fun from(
            host: String,
            firestorePort: Int,
        ): FirebaseEmulators? {
            val trimmed = host.trim()
            if (trimmed.isEmpty()) return null
            val port = firestorePort.takeIf { it in 1..65535 } ?: DEFAULT_FIRESTORE_PORT
            return FirebaseEmulators(trimmed, firestorePort = port)
        }
    }
}
