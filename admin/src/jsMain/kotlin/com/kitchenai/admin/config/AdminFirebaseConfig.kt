package com.kitchenai.admin.config

import kotlinx.serialization.Serializable

/** The web app's Firebase config, in the shape Hosting's `/__/firebase/init.json` serves it. */
@Serializable
data class AdminFirebaseConfig(
    val apiKey: String = "",
    val authDomain: String = "",
    val projectId: String = "",
    val storageBucket: String = "",
    val appId: String = "",
    /** Only in the local file: connects every SDK to the emulator ports in firebase.json. */
    val useEmulators: Boolean = false,
    val functionsRegion: String = DEFAULT_FUNCTIONS_REGION,
)

/** Same default as FUNCTIONS_REGION in functions/. */
const val DEFAULT_FUNCTIONS_REGION = "europe-southwest1"
