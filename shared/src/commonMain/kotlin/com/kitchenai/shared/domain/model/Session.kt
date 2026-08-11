package com.kitchenai.shared.domain.model

/**
 * Who is signed in. It carries no token, no credential and no email:
 * a credential held in domain state is a credential that ends up in a log.
 */
sealed interface Session {
    data object SignedOut : Session

    data class SignedIn(val userId: UserId, val isAnonymous: Boolean) : Session
}
