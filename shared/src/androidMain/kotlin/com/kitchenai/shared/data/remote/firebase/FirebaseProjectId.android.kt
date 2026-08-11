package com.kitchenai.shared.data.remote.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.android
import dev.gitlive.firebase.app

internal actual fun firebaseProjectId(): String? = Firebase.app.android.options.projectId
