package com.kitchenai.shared.data.remote.firebase

/**
 * The `projectId` from the native SDK.
 *
 * `FirebaseApp.options` is unusable: GitLive 2.5.0 force-unwraps `databaseURL`, null in any
 * project without Realtime Database, so on iOS it always throws. Android is unaffected.
 */
internal expect fun firebaseProjectId(): String?
