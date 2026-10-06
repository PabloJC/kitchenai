package com.kitchenai.ui.platform

import com.kitchenai.shared.domain.model.GoogleIdToken

/** What the platform sign-in sheet hands back. No email, no photo URL: just enough to sign in and to greet the user. */
data class GoogleSignInResult(val token: GoogleIdToken, val displayName: String?)
