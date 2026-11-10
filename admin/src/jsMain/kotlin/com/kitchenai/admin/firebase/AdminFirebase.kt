package com.kitchenai.admin.firebase

import com.kitchenai.admin.config.AdminFirebaseConfig
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.initialize
import dev.gitlive.firebase.storage.storage

private const val EMULATOR_HOST = "127.0.0.1"

/** Called once, before anything touches an SDK. Ports from firebase.json › emulators. */
fun initializeAdminFirebase(config: AdminFirebaseConfig) {
    Firebase.initialize(
        options =
            FirebaseOptions(
                applicationId = config.appId,
                apiKey = config.apiKey,
                projectId = config.projectId,
                storageBucket = config.storageBucket,
                authDomain = config.authDomain,
            ),
    )
    if (config.useEmulators) {
        Firebase.auth.useEmulator(EMULATOR_HOST, AUTH_PORT)
        Firebase.firestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
        Firebase.storage.useEmulator(EMULATOR_HOST, STORAGE_PORT)
        Firebase.functions(config.functionsRegion).useEmulator(EMULATOR_HOST, FUNCTIONS_PORT)
    }
}

private const val AUTH_PORT = 9099
private const val FIRESTORE_PORT = 8080
private const val STORAGE_PORT = 9199
private const val FUNCTIONS_PORT = 5001
