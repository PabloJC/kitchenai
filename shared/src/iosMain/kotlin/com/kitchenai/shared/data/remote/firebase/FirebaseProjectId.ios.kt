package com.kitchenai.shared.data.remote.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app
import dev.gitlive.firebase.ios
import kotlinx.cinterop.ExperimentalForeignApi

// `FIRApp` comes from a cinterop, hence the opt-in.
@OptIn(ExperimentalForeignApi::class)
internal actual fun firebaseProjectId(): String? = Firebase.app.ios.options.projectID
