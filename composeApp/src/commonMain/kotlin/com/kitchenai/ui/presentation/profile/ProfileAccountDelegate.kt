package com.kitchenai.ui.presentation.profile

import com.kitchenai.shared.domain.usecase.session.ObserveSessionUseCase
import com.kitchenai.shared.domain.usecase.session.SignInWithGoogleUseCase
import com.kitchenai.shared.domain.usecase.session.SignOutUseCase

/**
 * What the profile screen needs about the account rather than the profile document: the session
 * stream and signing in and out with Google. Grouped so the ViewModel takes one collaborator for
 * this role instead of three (#189); it holds no logic and decides nothing.
 */
class ProfileAccountDelegate(
    val observeSession: ObserveSessionUseCase,
    val signInWithGoogle: SignInWithGoogleUseCase,
    val signOut: SignOutUseCase,
)
