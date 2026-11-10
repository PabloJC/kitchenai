@file:JsModule("firebase/auth")
@file:JsNonModule

package com.kitchenai.admin.auth

import kotlin.js.Promise

// GitLive's common API has no popup sign-in; these come from the same Firebase JS SDK it wraps.
external class GoogleAuthProvider

external fun signInWithPopup(
    auth: dynamic,
    provider: GoogleAuthProvider,
): Promise<dynamic>
