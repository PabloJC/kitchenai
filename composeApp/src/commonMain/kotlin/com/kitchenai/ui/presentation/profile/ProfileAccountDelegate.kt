package com.kitchenai.ui.presentation.profile

import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.session.ObserveSessionUseCase
import com.kitchenai.shared.domain.usecase.session.SignInWithGoogleUseCase
import com.kitchenai.shared.domain.usecase.session.SignOutUseCase

/**
 * What the profile screen needs about the account rather than the profile document: the session
 * stream, signing in and out with Google, and the clock a freshly-seeded profile stamps itself
 * with. Grouped so the ViewModel takes one collaborator for this role instead of four (#189); it
 * holds no logic and decides nothing.
 */
class ProfileAccountDelegate(
    val observeSession: ObserveSessionUseCase,
    val signInWithGoogle: SignInWithGoogleUseCase,
    val signOut: SignOutUseCase,
    val time: TimeProvider,
)
